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

package com.exactpro.th2.act.core.handlers

import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.routers.EventSubmissionException
import com.exactpro.th2.act.core.routers.MessageSubmissionException
import com.exactpro.th2.common.grpc.RequestStatus
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class RequestMessageSubmitter(private val respondOnSuccess: Boolean = false): RequestHandler() {

    override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {

        try {
            requestContext.messageBatchRouter.sendMessage(
                message = request.requestMessage,
                parentEventID = requestContext.parentEventID
            )
            requestContext.eventBatchRouter.createSendMessageEvent(
                message = request.requestMessage,
                parentEventID = requestContext.parentEventID
            )

            super.toNextHandler(request, responder, requestContext)

            if (respondOnSuccess) {
                responder.onResponseFound(RequestStatus.Status.SUCCESS, requestContext.checkpoint, listOf())
            }

        } catch (e: MessageSubmissionException) {
            LOGGER.error("Failed to submit a message.", e)
            responder.onError("Failed to submit a message: ${e.message}")
        } catch (e: EventSubmissionException) {
            LOGGER.error("Failed to submit a \"Send Message\" Event.", e)
            responder.onError("Failed to submit a \"Send Message\" Event: ${e.message}")
        }
    }
}
