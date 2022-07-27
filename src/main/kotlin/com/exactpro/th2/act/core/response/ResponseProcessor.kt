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

import com.exactpro.th2.act.core.action.FailedResponseFoundException
import com.exactpro.th2.act.core.messages.MessageMatches
import com.exactpro.th2.act.core.action.NoResponseFoundException
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.common.grpc.MessageID
import java.util.stream.Collectors

class ResponseProcessor(
    private val noResponseBodyFactory: IBodyDataFactory,
): IResponseProcessor{

    override fun process(
        messagesMatches: List<MessageMatches>,
        processedMessageIDs: Collection<MessageID>,
        requestContext: RequestContext
    ) {
        if (messagesMatches.isEmpty()) {
            requestContext.eventBatchRouter.createNoResponseEvent(
                noResponseBodyFactory = noResponseBodyFactory,
                processedMessageIDs = processedMessageIDs,
                parentEventID = requestContext.parentEventID
            )
            throw NoResponseFoundException("Unexpected behavior. The message to receive was not found.")
        } else {
            messagesMatches.forEach {
                if (it.isMatchesFail()) {
                    requestContext.eventBatchRouter.createErrorEvent(
                        cause = "Found a message for failOn.",
                        parentEventID = requestContext.parentEventID
                    )
                    throw FailedResponseFoundException("Found a message for failOn.")
                } else {
                    requestContext.eventBatchRouter.createResponseReceivedEvents(
                        messages = messagesMatches.stream().map { msgMatches -> msgMatches.message }.collect(Collectors.toList()),
                        eventStatus = it.status,
                        parentEventID = requestContext.parentEventID
                    )
                }
            }
        }
    }
}