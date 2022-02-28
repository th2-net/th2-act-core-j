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

import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.randomRequest
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.toEventID
import com.exactpro.th2.common.grpc.Checkpoint
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

internal class TestRequestLogger {

    /* Test List for RequestLogger:
     * 1) Should log request, rpc name and request name to logger. V
     * 2) Should pass the request to the next handler. V
     */

    /**
     * Creates a [RequestContext] from the specified event batch router.
     */
    private fun createRequestContext(): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = mockk { },
        eventBatchRouter = mockk { },
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance()
    )

    private lateinit var request: IRequest
    private lateinit var responder: IResponder
    private lateinit var nextHandler: IRequestHandler
    private lateinit var requestContext: RequestContext
    private lateinit var logger: KLogger

    @BeforeEach
    internal fun setUp() {
        request = randomRequest()
        responder = mockk { }
        nextHandler = mockk { justRun { handle(any(), any(), any()) } }
        requestContext = createRequestContext()

        logger = KotlinLogging.logger(Logger.ROOT_LOGGER_NAME)
    }

    @Test
    fun `test should log request`() {
        val logAppender: Appender = mockk { justRun { doAppend(any()) } }
        LogManager.getRootLogger().addAppender(logAppender)

        RequestLogger(logger).chain(nextHandler).handle(request, responder, requestContext)

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
    fun `test should pass request to next handler`() {
        RequestLogger(logger).chain(nextHandler).handle(request, responder, requestContext)

        verify { nextHandler.handle(request, responder, requestContext) }
    }
}
