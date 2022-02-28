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

package com.exactpro.th2.act.core.handlers.decorators

import com.exactpro.th2.act.core.handlers.IRequestHandler
import com.exactpro.th2.act.core.managers.ISubscriptionManager
import com.exactpro.th2.act.core.monitors.MessageResponseMonitor
import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.rules.EchoCheckRule
import com.exactpro.th2.common.grpc.Direction
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class MessageEchoValidator(
    handler: IRequestHandler,
    private val subscriptionManager: ISubscriptionManager,
    private val timeout: Long = 5_000
): AbstractRequestHandlerDecorator(handler) {

    override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {

        val responseMonitor = MessageResponseMonitor()
        val checkRule = EchoCheckRule(request.requestMessage, requestContext.parentEventID)

        MessageReceiver(subscriptionManager, responseMonitor, checkRule, direction = Direction.SECOND).use {
            super.handle(request, responder, requestContext)

            val timeElapsed = measureTimeMillis { responseMonitor.await(timeout, TimeUnit.MILLISECONDS) }

            if (responseMonitor.isNotified) {
                LOGGER.debug { "Received Echo in $timeElapsed ms for the request: ${request.debugString}" }
            } else {
                val errorCause = "The message for ${requestContext.rpcName} could not be sent."
                requestContext.eventBatchRouter.createErrorEvent(errorCause, requestContext.parentEventID)

                if (!responder.isResponseSent) {
                    responder.onError(errorCause)
                }
            }
        }
    }
}
