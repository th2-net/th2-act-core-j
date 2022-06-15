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

import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.EventSubmissionException
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.core.routers.MessageSubmissionException
import com.exactpro.th2.act.randomRequest
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.toEventID
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.RequestStatus
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TestRequestMessageSubmitter {

    /* Test List for RequestMessageSubmitter:
     * 1) Should submit message to message router. V
     * 2) Should submit a send message event to the event router. V
     * 3) Should pass the request to the next handler if successful. V
     * 4) Should not send an error response to the client if successful. V
     * 5) Should send an error response to the client if an error occurs while sending the message. V
     * 6) Should not pass the request to the next handler if an error occurs while sending the message. V
     * 7) Should send a success response to the client if the message was successfully sent and the 'respondOnSuccess'
     *    flag was set. V
     * 8) Should not send a success response to the client if the 'respondOnSuccess' flag is not set. V
     */

    /**
     * Creates a [RequestContext] from the specified message batch and event batch routers.
     */
    private fun createRequestContext(messageRouter: MessageRouter, eventRouter: EventRouter): RequestContext =
        RequestContext(
            rpcName = randomString(),
            requestName = randomString(),
            messageBatchRouter = messageRouter,
            eventBatchRouter = eventRouter,
            parentEventID = randomString().toEventID(),
            checkpoint = Checkpoint.getDefaultInstance(),
            SubscriptionManager(),
            1000
        )

    private lateinit var messageSubmitter: RequestMessageSubmitter
    private lateinit var request: IRequest
    private lateinit var responder: IResponder
    private lateinit var messageRouter: MessageRouter
    private lateinit var eventRouter: EventRouter
    private lateinit var nextHandler: IRequestHandler
    private lateinit var requestContext: RequestContext

    @BeforeEach
    internal fun setUp() {
        messageSubmitter = RequestMessageSubmitter()
        request = randomRequest()
        responder = mockk { }
        nextHandler = mockk { }

        messageRouter = mockk {
            justRun { sendMessage(any(), any(), any()) }
        }

        eventRouter = mockk {
            every { createSendMessageEvent(any(), any()) } answers { randomString().toEventID() }
        }

        requestContext = createRequestContext(messageRouter = messageRouter, eventRouter = eventRouter)
    }

    @Test
    fun `test should submit message to message router`() {
        messageSubmitter.handle(request, responder, requestContext)

        verify { messageRouter.sendMessage(request.requestMessage, parentEventID = requestContext.parentEventID) }
    }

    @Test
    fun `test should submit send message event to event router`() {
        messageSubmitter.handle(request, responder, requestContext)

        verify {
            eventRouter.createSendMessageEvent(request.requestMessage, parentEventID = requestContext.parentEventID)
        }
    }

    @Test
    fun `test should pass request to next handler`() {
        val nextHandler: IRequestHandler = mockk(relaxUnitFun = true) { }

        messageSubmitter.chain(nextHandler).handle(request, responder, requestContext)

        verify { nextHandler.handle(request, responder, requestContext) }
    }

    @Test
    fun `test should send error response to in case of a message submission error`() {
        val nextHandler: IRequestHandler = mockk(relaxUnitFun = true) { }

        every { responder.onError(any()) } just Runs
        every { messageRouter.sendMessage(any(), any(), any()) } throws MessageSubmissionException()

        messageSubmitter.chain(nextHandler).handle(request, responder, requestContext)

        verify { responder.onError(any()) }
        verify(exactly = 0) { nextHandler.handle(any(), any(), any()) }
    }

    @Test
    fun `test should send error response to in case of an event submission error`() {
        val nextHandler: IRequestHandler = mockk(relaxUnitFun = true) { }

        every { responder.onError(any()) } just Runs
        every { eventRouter.createSendMessageEvent(any(), any()) } throws EventSubmissionException()

        messageSubmitter.chain(nextHandler).handle(request, responder, requestContext)

        verify { responder.onError(any()) }
        verify(exactly = 0) { nextHandler.handle(any(), any(), any()) }
    }

    @Test
    fun `test should send success response to client`() {
        val messageSubmitter = RequestMessageSubmitter(respondOnSuccess = true)
        val nextHandler: IRequestHandler = mockk(relaxUnitFun = true) { }

        every { responder.onResponseFound(any(), any(), any()) } just Runs
        every { eventRouter.createSendMessageEvent(any(), any()) } answers { randomString().toEventID() }

        messageSubmitter.chain(nextHandler).handle(request, responder, requestContext)

        verify { nextHandler.handle(request, responder, requestContext) }
        verify { responder.onResponseFound(RequestStatus.Status.SUCCESS, requestContext.checkpoint, listOf()) }
    }

    @Test
    fun `test should not send success response to client when flag isn't set`() {
        val messageSubmitter = RequestMessageSubmitter(respondOnSuccess = false)
        val nextHandler: IRequestHandler = mockk(relaxUnitFun = true) { }

        every { eventRouter.createSendMessageEvent(any(), any()) } answers { randomString().toEventID() }

        messageSubmitter.chain(nextHandler).handle(request, responder, requestContext)

        verify { nextHandler.handle(request, responder, requestContext) }
        verify(exactly = 0) { responder.onResponseFound(any(), any(), any()) }
    }
}
