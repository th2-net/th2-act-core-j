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
import com.exactpro.th2.act.core.monitors.MessageResponseMonitor
import com.exactpro.th2.act.core.receivers.IMessageReceiverFactory
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.response.IResponseProcessor
import io.grpc.Context
import io.grpc.Deadline
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class SystemResponseReceiver(
    requestHandler: IRequestHandler,
    private val messageReceiverFactory: IMessageReceiverFactory,
    private val responseProcessor: IResponseProcessor,
    private val responseTimeoutMillis: Long = 10_000
): AbstractRequestHandlerDecorator(requestHandler) {

    override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {

        val monitor = MessageResponseMonitor()

        messageReceiverFactory.from(monitor).use { receiver ->

            super.handle(request, responder, requestContext)

            if (responder.isResponseSent) {
                return
            }

            val timeout = requestContext.requestDeadline
            LOGGER.info("Synchronization timeout: {} ms", timeout)

            val elapsed = measureTimeMillis { monitor.await(timeout, TimeUnit.MILLISECONDS) }

            if (monitor.isNotified) {
                LOGGER.debug("Response Monitor notified in $elapsed ms for ${requestContext.rpcName}")
            }

            if (Context.current().isCancelled) {
                val cause = Context.current().cancellationCause()
                LOGGER.error("The request was cancelled by the client.", cause)
                responder.onError("The request was cancelled by the client. Reason: ${cause?.message}")
            }

            responseProcessor.process(
                receiver.responseMessages, receiver.processedMessageIDs, responder, requestContext
            )
        }
    }
}
