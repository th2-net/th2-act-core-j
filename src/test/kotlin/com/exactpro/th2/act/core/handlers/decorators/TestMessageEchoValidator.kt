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
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.EventID
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TestMessageEchoValidator {

    /* Test List for MessageEchoValidator:
     * 1) Should pass request to wrapped handler. V
     * 2) Should capture the request message echo (if message was sent). V
     * 3) Should send error response if the request message echo could not be found. V
     * 4) Should create error event if the request message echo could not be found. V
     */

    /**
     * Creates a [RequestContext].
     */
    private fun createRequestContext(eventBatchRouter: EventRouter, parentEventID: EventID): RequestContext =
        RequestContext(
            rpcName = randomString(),
            requestName = randomString(),
            messageBatchRouter = mockk { },
            eventBatchRouter = eventBatchRouter,
            parentEventID = parentEventID,
            checkpoint = Checkpoint.getDefaultInstance()
        )

    private lateinit var request: IRequest
    private lateinit var responder: IResponder
    private lateinit var handler: IRequestHandler
    private lateinit var requestContext: RequestContext
    private lateinit var eventBatchRouter: EventRouter
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeEach
    internal fun setUp() {
        val parentEventID = randomString().toEventID()  // This parent event ID is also set in the request message.

        request = Request(randomMessage(direction = Direction.SECOND, parentEventID = parentEventID))
        responder = mockk { }
        handler = mockk { }
        eventBatchRouter = mockk { }
        requestContext = createRequestContext(eventBatchRouter, parentEventID = parentEventID)
        subscriptionManager = SubscriptionManager()

        justRun { handler.handle(any(), any(), any()) }
        every { responder.isResponseSent } returns false
    }

    @Test
    fun `test should pass request to wrapped handler`() {
        every { responder.onError(any()) } just Runs
        every { eventBatchRouter.createErrorEvent(any(), any()) } answers { randomString().toEventID() }

        MessageEchoValidator(
            handler = handler,
            subscriptionManager = subscriptionManager,
            timeout = 1
        ).handle(request, responder, requestContext)

        verify { handler.handle(request, responder, requestContext) }
    }

    @Test
    fun `test should capture request message echo`() {
        every { handler.handle(request, responder, requestContext) } answers {
            subscriptionManager.handler(randomString(), request.requestMessage.toBatch())
        }

        MessageEchoValidator(
            handler = handler,
            subscriptionManager = subscriptionManager,
            timeout = 500
        ).handle(request, responder, requestContext)
    }

    @Test
    fun `test should send error response to client`() {
        every { responder.onError(any()) } just Runs
        every { eventBatchRouter.createErrorEvent(any(), any()) } answers { randomString().toEventID() }

        val connectionID = randomConnectionID()
        val expectedMessageType = randomMessageType()
        val expectedParentEventID = "Expected ID".toEventID()
        val request = Request(
            requestMessage = expectedMessageType.toRandomMessage(
                connectionID = connectionID,
                parentEventID = expectedParentEventID
            )
        )

        val randomMessages = 10.randomMessageTypes(expectedMessageType).map {
            it.toRandomMessage(connectionID = connectionID, parentEventID = "Unexpected ID".toEventID())
        }

        every { handler.handle(request, responder, requestContext) } answers {
            subscriptionManager.handler(randomString(), randomMessages.toBatch())
        }

        MessageEchoValidator(
            handler = handler,
            subscriptionManager = subscriptionManager,
            timeout = 500
        ).handle(request, responder, requestContext)

        verify { responder.onError(any()) }
        verify { eventBatchRouter.createErrorEvent(any(), requestContext.parentEventID) }
    }

    @Test
    fun `test should not send error response if response is already sent`() {
        every { eventBatchRouter.createErrorEvent(any(), any()) } answers { randomString().toEventID() }
        every { responder.isResponseSent } returns true

        MessageEchoValidator(
            handler = handler,
            subscriptionManager = subscriptionManager,
            timeout = 1
        ).handle(request, responder, requestContext)

        verify(exactly = 0) { responder.onError(any()) }
    }
}
