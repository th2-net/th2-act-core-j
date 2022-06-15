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

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.handlers.IRequestHandler
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.response.IResponseProcessor
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.stubs.StubMessageReceiverFactory
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.act.stubs.fail
import com.exactpro.th2.common.grpc.*
import io.grpc.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import kotlin.system.measureTimeMillis

internal class TestSystemResponseReceiver {

    /* Test List for SystemResponseReceiver:
     * 1) Should create SystemResponseReceiver from a Message Receiver Factory and a Response Processor. V
     * 2) Should wait for the system response after passing the request to the decorated handler. V
     * 3) Should pass received messages to response processor. V
     */

    class StubResponder: IResponder {

        var responseIsSent: Boolean = false
        var sentStatus: RequestStatus.Status? = null

        /**
         * Marks that an error response was sent.
         */
        override fun onError(cause: String) {
            sentStatus = RequestStatus.Status.ERROR
        }

        /**
         * Returns `true` if configured so, otherwise `false`.
         */
        override fun isResponseSent() = responseIsSent

        // ---------------------------------------------------------------------------------------------------------- //

        override fun onResponseFound(
            status: RequestStatus.Status, checkpoint: Checkpoint, responses: MutableList<Message>
        ) = fail()
    }

    class StubRequestHandler: IRequestHandler {

        private var response: () -> Unit = {}

        lateinit var request: IRequest
        lateinit var responder: IResponder
        lateinit var requestContext: RequestContext

        /**
         * Will execute the specified callback whenever a request is passed to this handler.
         */
        infix fun respondsWith(response: () -> Unit) {
            this.response = response
        }

        /**
         * Keeps the last request, responder and request context that was passed.
         */
        override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {
            this.request = request
            this.responder = responder
            this.requestContext = requestContext

            response()
        }

        // ---------------------------------------------------------------------------------------------------------- //

        override fun chain(handler: IRequestHandler) = fail()
    }

    class StubResponseProcessor: IResponseProcessor {

        lateinit var responseMessages: MutableList<Message>
        lateinit var processedMessageIDs: MutableCollection<MessageID>
        lateinit var responder: IResponder
        lateinit var requestContext: RequestContext

        /**
         * Keeps the last arguments that were passed.
         */
        override fun process(
            responseMessages: MutableList<Message>,
            processedMessageIDs: MutableCollection<MessageID>,
            responder: IResponder,
            requestContext: RequestContext
        ) {
            this.responseMessages = responseMessages
            this.processedMessageIDs = processedMessageIDs
            this.responder = responder
            this.requestContext = requestContext
        }
    }

    /**
     * Creates a [RequestContext].
     */
    private fun createRequestContext(rpcContext: Context = Context.current()): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = MessageRouter(StubMessageRouter()),
        eventBatchRouter = EventRouter(StubMessageRouter()),
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance(),
        SubscriptionManager(),
        1000
    )

    private lateinit var request: IRequest
    private lateinit var responder: StubResponder
    private lateinit var requestContext: RequestContext
    private lateinit var handler: StubRequestHandler
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var receiverFactory: StubMessageReceiverFactory
    private lateinit var responseProcessor: StubResponseProcessor

    @BeforeEach
    internal fun setUp() {
        request = randomRequest()
        responder = StubResponder()
        requestContext = createRequestContext()
        handler = StubRequestHandler()
        subscriptionManager = SubscriptionManager()
        responseProcessor = StubResponseProcessor()

        receiverFactory = StubMessageReceiverFactory(
            subscriptionManager = subscriptionManager,
            recipientConnection = randomConnectionID(),
            requestMessage = randomMessage(),
            expectedMessageType = randomMessageType()
        )
    }

    @Test
    fun `test should create response receiver`() {
        SystemResponseReceiver(requestHandler = handler,
                               messageReceiverFactory = receiverFactory,
                               responseProcessor = responseProcessor)
    }

    @Test
    fun `test should call wrapped handler`() {
        val decoratedHandler = SystemResponseReceiver(requestHandler = handler,
                                                      messageReceiverFactory = receiverFactory,
                                                      responseProcessor = responseProcessor,
                                                      responseTimeoutMillis = 1) // So the test will execute fast.

        decoratedHandler.handle(request, responder, requestContext)

        expect {
            that(handler.request).isSameInstanceAs(request)
            that(handler.responder).isSameInstanceAs(responder)
            that(handler.requestContext).isSameInstanceAs(requestContext)
        }
    }

    @Test
    fun `test should wait for a response until timeout`() {
        val timeout = 100L
        val decoratedHandler = SystemResponseReceiver(requestHandler = handler,
                                                      messageReceiverFactory = receiverFactory,
                                                      responseProcessor = responseProcessor,
                                                      responseTimeoutMillis = timeout)

        val elapsedTime = measureTimeMillis { decoratedHandler.handle(request, responder, requestContext) }

        expect {
            that(elapsedTime).isGreaterThan(timeout)
        }
    }

    @Test
    fun `test should receive response message`() {
        val connectionID = randomConnectionID()
        val clOrdID = randomString()
        val requestMessage = randomMessageType().toMessage(connectionID, TestField.CLIENT_ORDER_ID to clOrdID)
        val receiverFactory = StubMessageReceiverFactory(
            subscriptionManager = subscriptionManager,
            recipientConnection = connectionID,
            requestMessage = requestMessage,
            expectedMessageType = TestMessageType.EXECUTION_REPORT
        )

        val responseReceiver = SystemResponseReceiver(
            requestHandler = handler,
            messageReceiverFactory = receiverFactory,
            responseProcessor = responseProcessor,
            responseTimeoutMillis = 250
        )

        val expectedMessage = TestMessageType.EXECUTION_REPORT.toMessage(
            connectionID = connectionID,
            fields = 5.randomFields().toMap(),
            direction = Direction.FIRST
        )

        // Passing some random messages + the expected message.
        val receivedMessages = 5.randomMessageTypes(exclude = arrayOf(TestMessageType.EXECUTION_REPORT)).map {
            it.toRandomMessage(connectionID, direction = Direction.FIRST)
        }.plus(expectedMessage)

        val expectedMessageIDs = receivedMessages.map { it.metadata.id }

        handler respondsWith {
            receivedMessages.forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        responseReceiver.handle(request, responder, requestContext)

        expect {
            that(responseProcessor.responder).isSameInstanceAs(responder)
            that(responseProcessor.requestContext).isSameInstanceAs(requestContext)
            that(responseProcessor.responseMessages).containsExactly(expectedMessage)
            that(responseProcessor.processedMessageIDs).containsExactlyInAnyOrder(expectedMessageIDs)
        }
    }

    @Test
    fun `test should send error response to client if rpc context was canceled`() {
        val decoratedHandler = SystemResponseReceiver(requestHandler = handler,
                                                      messageReceiverFactory = receiverFactory,
                                                      responseProcessor = responseProcessor,
                                                      responseTimeoutMillis = 100)

        val context = Context.current().withCancellation()

        runBlocking {
            launch { decoratedHandler.handle(request, responder, createRequestContext(context)) }
            context.cancel(Throwable("Just for testing."))
        }

        expect {
            that(responder.sentStatus).isEqualTo(RequestStatus.Status.ERROR)
        }
    }

    @Test
    fun `test should not send error response to client if a response was already sent`() {
        val decoratedHandler = SystemResponseReceiver(requestHandler = handler,
                                                      messageReceiverFactory = receiverFactory,
                                                      responseProcessor = responseProcessor,
                                                      responseTimeoutMillis = 100)

        responder.responseIsSent = true
        val context = Context.current().withCancellation()

        runBlocking {
            launch { decoratedHandler.handle(request, responder, createRequestContext(context)) }
            context.cancel(Throwable("Just for testing."))
        }

        expectThat(responder.sentStatus).isNull()
    }
}
