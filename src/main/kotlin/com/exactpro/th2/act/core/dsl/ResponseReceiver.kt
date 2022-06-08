package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.handlers.IRequestHandler
import com.exactpro.th2.act.core.handlers.decorators.AbstractRequestHandlerDecorator
import com.exactpro.th2.act.core.monitors.MessageResponseMonitor
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.response.IResponseProcessor
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class ResponseReceiver(requestHandler: IRequestHandler,
                       messageReceiverFactory: MessageReceiverFactory
): AbstractRequestHandlerDecorator(requestHandler) {

    private lateinit var responseProcessor: IResponseProcessor
    private var responseTimeoutMillis by Delegates.notNull<Long>()

    private val monitor = MessageResponseMonitor()
    private val messagesReceiver = messageReceiverFactory.from(monitor)

    fun setData(responseProcessor: IResponseProcessor, responseTimeoutMillis: Long){
        this.responseProcessor = responseProcessor
        this.responseTimeoutMillis = responseTimeoutMillis
    }

    fun cleanBuffer(){
        messagesReceiver.cleanMatchedMessages()
    }

    override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {

        messagesReceiver.use  { receiver ->

            super.handle(request, responder, requestContext)

            if (responder.isResponseSent) {
                return
            }

            LOGGER.info("Synchronization timeout: {} ms", responseTimeoutMillis)
            val elapsed = measureTimeMillis { monitor.await(responseTimeoutMillis, TimeUnit.MILLISECONDS) }

            if (monitor.isNotified) {
                LOGGER.debug("Response Monitor notified in $elapsed ms for ${requestContext.rpcName}")
            }

            if (requestContext.isCancelled) {
                val cause = requestContext.cancellationCause
                LOGGER.error("The request was cancelled by the client.", cause)
                responder.onError("The request was cancelled by the client. Reason: ${cause?.message}")
            }

            responseProcessor.process(
                receiver.responseMessages, receiver.processedMessageIDs, responder, requestContext
            )
        }
    }
}