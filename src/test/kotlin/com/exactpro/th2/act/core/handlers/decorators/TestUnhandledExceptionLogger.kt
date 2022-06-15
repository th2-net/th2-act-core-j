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
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.randomRequest
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.toEventID
import com.exactpro.th2.common.grpc.Checkpoint
import io.mockk.*
import mu.KLogger
import mu.KotlinLogging
import org.apache.log4j.Appender
import org.apache.log4j.LogManager
import org.apache.log4j.spi.LoggingEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import strikt.api.expect
import strikt.assertions.contains

internal class TestUnhandledExceptionLogger {

    /* Test List for UnhandledExceptionLogger:
     * 1) Should log any unhandled exception thrown by decorated handler. V
     * 2) Should send an error response to the client if an unhandled exception is thrown. V
     * 3) Should not send an error response if no error is thrown. V
     */

    /**
     * Creates a [RequestContext].
     */
    private fun createRequestContext(): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = mockk { },
        eventBatchRouter = mockk { },
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance(),
        SubscriptionManager(),
        1000
    )

    private lateinit var request: IRequest
    private lateinit var responder: IResponder
    private lateinit var handler: IRequestHandler
    private lateinit var requestContext: RequestContext
    private lateinit var logger: KLogger

    @BeforeEach
    internal fun setUp() {
        request = randomRequest()
        responder = mockk { }
        handler = mockk { }
        requestContext = createRequestContext()

        logger = KotlinLogging.logger(Logger.ROOT_LOGGER_NAME)
    }

    @Test
    fun `test should log unhandled exception`() {
        every { handler.handle(any(), any(), any()) } throws Exception()
        every { responder.onError(any()) } just Runs

        val logAppender: Appender = mockk(relaxed = true) { }
        LogManager.getRootLogger().addAppender(logAppender)

        UnhandledExceptionLogger(handler, logger).handle(request, responder, requestContext)

        val logEventSlot = slot<LoggingEvent>()
        verify { logAppender.doAppend(capture(logEventSlot)) }

        expect {
            that(logEventSlot.captured.message as String) {
                contains(request.debugString)
                contains(requestContext.rpcName)
                contains(requestContext.requestName)
            }
        }
    }

    @Test
    fun `test should send error response to client`() {
        every { handler.handle(any(), any(), any()) } throws Exception()
        every { responder.onError(any()) } just Runs

        UnhandledExceptionLogger(handler, logger).handle(request, responder, requestContext)

        val errorCauseSlot = slot<String>()
        verify { responder.onError(capture(errorCauseSlot)) }

        expect {
            that(errorCauseSlot.captured) {
                contains(requestContext.rpcName)
                contains(requestContext.requestName)
            }
        }
    }

    @Test
    fun `test should pass request to wrapped handler`() {
        justRun { handler.handle(any(), any(), any()) }

        UnhandledExceptionLogger(handler, logger).handle(request, responder, requestContext)

        verify { handler.handle(request, responder, requestContext) }
    }

    @Test
    fun `test should chain with next handler`() {
        every { handler.chain(any()) } returns handler

        val nextHandler = mockk<IRequestHandler>()
        UnhandledExceptionLogger(handler, logger).chain(nextHandler)

        verify { handler.chain(nextHandler) }
    }
}
