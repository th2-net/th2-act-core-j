/*
 * Copyright 2022-2022 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.act.core.response

import com.exactpro.th2.act.core.dsl.NoResponseFoundException
import com.exactpro.th2.act.core.messages.MessageMapping
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RequestStatus
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class ResponseProcessor(
    private val expectedMessages: Collection<MessageMapping>,
    private val noResponseBodyFactory: IBodyDataFactory
): IResponseProcessor {

    override fun process(
        responseMessages: List<Message>,
        processedMessageIDs: Collection<MessageID>,
        responder: IResponder,
        requestContext: RequestContext,
    ) {
        val status: RequestStatus.Status

        if (responseMessages.isEmpty()) {
            requestContext.eventBatchRouter.createNoResponseEvent(
                noResponseBodyFactory = noResponseBodyFactory,
                processedMessageIDs = processedMessageIDs,
                parentEventID = requestContext.parentEventID
            )
            status = RequestStatus.Status.ERROR
            throw NoResponseFoundException("Unexpected behavior. The message to receive was not found.")
        } else {
            val responseMessageTypes = responseMessages.map { it.metadata.messageType }
            val matchingMapping = expectedMessages.find { it.matches(responseMessageTypes) }

            status = if (matchingMapping == null) {
                requestContext.eventBatchRouter.createNoMappingEvent(
                    expectedMappings = expectedMessages,
                    receivedMessages = responseMessages,
                    parentEventID = requestContext.parentEventID
                )
                RequestStatus.Status.ERROR
            } else {
                requestContext.eventBatchRouter.createResponseReceivedEvents(
                    messages = responseMessages,
                    eventStatus = matchingMapping.statusMapping.eventStatus,
                    parentEventID = requestContext.parentEventID
                )
                matchingMapping.statusMapping.requestStatus
            }
        }

        if (!responder.isResponseSent) {
            responder.onResponseFound(status, requestContext.checkpoint, responseMessages)
        } else {
            LOGGER.warn {
                """Could not send response to client as one was already sent.
                  |Request Status: $status
                  |Request Checkpoint: ${requestContext.checkpoint}
                  |Received Response Messages: $responseMessages
                """.trimMargin()
            }
        }
    }
}