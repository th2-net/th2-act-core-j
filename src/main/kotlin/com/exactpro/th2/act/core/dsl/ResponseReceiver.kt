package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.monitors.MessageResponseMonitor
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.response.IResponseProcessor
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class ResponseReceiver(
    messageReceiverFactory: MessageReceiverFactory
) {
    private val messagesReceiver = messageReceiverFactory.from(MessageResponseMonitor())

    fun handle(
        responder: IResponder,
        requestContext: RequestContext,
        responseProcessor: IResponseProcessor,
        responseTimeoutMillis: Long
    ) {
        val monitor = MessageResponseMonitor()

        messagesReceiver.use { receiver ->
            receiver.reloadMonitor(monitor)

            if (responder.isResponseSent) {
                return
            }
            LOGGER.info("Synchronization timeout: $responseTimeoutMillis ms")
            val elapsed = measureTimeMillis { monitor.await(responseTimeoutMillis, TimeUnit.MILLISECONDS) }

            if (monitor.isNotified) {
                LOGGER.debug("Response Monitor notified in $elapsed ms for ${requestContext.rpcName}")
            }

            responseProcessor.process(
                receiver.responseMessages, receiver.processedMessageIDs, responder, requestContext
            )
        }
    }
}