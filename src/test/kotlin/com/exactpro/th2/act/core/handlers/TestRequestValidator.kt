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

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.handlers.validations.IRequestValidation
import com.exactpro.th2.act.core.handlers.validations.ValidationResult
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.common.grpc.Checkpoint
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.contains

internal class TestRequestValidator {

    /*
     * Test List for RequestValidator:
     * 1) Should pass a valid request to the next handler. V
     * 2) Should not pass an invalid request to the next handler. V
     * 3) Should not send a response to the client when passed a valid request. V
     * 4) Should not send an error event to the Event Router when passed a valid request. V
     * 5) Should send an error response to the client when passed an invalid request. V
     * 6) Should send an error event to the Event Router when passed an invalid request. V
     */

    /**
     * Creates a [RequestContext] from the specified event batch router.
     */
    private fun EventRouter.createRequestContext(): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = mockk { },
        eventBatchRouter = this,
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance(),
        SubscriptionManager(),
        1000
    )

    private lateinit var responder: IResponder
    private lateinit var eventRouter: EventRouter
    private lateinit var nextHandler: IRequestHandler
    private lateinit var requestContext: RequestContext

    @BeforeEach
    internal fun setUp() {
        responder = mockk { }
        eventRouter = mockk { }
        nextHandler = mockk { }

        requestContext = eventRouter.createRequestContext()
    }

    @Test
    fun `test should pass a valid request to the next handler`() {
        val validation = TestMessageType.REJECT.toValidation()
        val validRequest = Request(TestMessageType.REJECT.toRandomMessage())

        justRun { nextHandler.handle(any(), any(), any()) }

        RequestValidator(validation).chain(nextHandler).handle(validRequest, responder, requestContext)

        verify { nextHandler.handle(validRequest, responder, requestContext) }
        verify { responder wasNot Called }
    }

    @Test
    fun `test should not pass an invalid request to the next handler`() {
        val validations = listOf(TestMessageType.REJECT.toValidation(), TestMessageType.NEW_ORDER_SINGLE.toValidation())
        val invalidRequest = Request(TestMessageType.REJECT.toRandomMessage())

        every { responder.onError(any()) } just Runs
        every { eventRouter.createErrorEvent(any(), any()) } answers { randomString().toEventID() }

        RequestValidator(validations).chain(nextHandler).handle(
            invalidRequest, responder, requestContext
        )

        verify { responder.onError(any()) }
        verify(exactly = 0) { nextHandler.handle(any(), any(), any()) }
    }

    @Test
    fun `test should not pass an error event to the event router when a valid request is handled`() {
        // This validator won't have any validations, so it should pass.
        RequestValidator().handle(randomRequest(), responder, requestContext)

        verify { eventRouter wasNot Called }
    }

    @Test
    fun `test should pass an error event to the event router when an invalid request is handled`() {
        val result = ValidationResult.invalid(reason = randomString())
        val validation = IRequestValidation { result }

        every { responder.onError(any()) } just Runs
        every { eventRouter.createErrorEvent(any(), any()) } answers { randomString().toEventID() }

        RequestValidator(validation).chain(nextHandler).handle(randomRequest(), responder, requestContext)

        val reasonSlot = slot<String>()

        verify { eventRouter.createErrorEvent(capture(reasonSlot), requestContext.parentEventID) }

        expect {
            that(reasonSlot.captured).contains(result.invalidationReason)
        }
    }
}
