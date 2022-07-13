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
import com.exactpro.th2.act.stubs.fail
import io.mockk.spyk
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.isSameInstanceAs

internal class TestRequestHandler {

    /* Test List for RequestHandler:
     * 1) Should chain with the handler passed to it, if it is not already chained. V
     * 2) Should invoke the chain method of the next handler if it is already chained. V
     * 3) Should throw CircularChainingException if chained with itself. V
     */

    class StubRequestHandler: IRequestHandler {
        lateinit var nextHandler: IRequestHandler

        /**
         * Keeps the last handler that was passed. Returns itself.
         */
        override fun chain(handler: IRequestHandler) = this.also { nextHandler = handler }

        // ---------------------------------------------------------------------------------------------------------- //

        override fun handle(request: IRequest, requestContext: RequestContext) = fail()
    }

    @Test
    fun `test should chain with handler`() {
        val rootHandler: RequestHandler = spyk { }
        val nextHandler = StubRequestHandler()

        expect {
            that(rootHandler.chain(nextHandler)).isSameInstanceAs(rootHandler)
        }
    }

    @Test
    fun `test should chain with multiple handlers`() {
        val rootHandler: RequestHandler = spyk { }
        val firstHandler = StubRequestHandler()
        val secondHandler = StubRequestHandler()

        rootHandler.chain(firstHandler).chain(secondHandler)

        expect {
            that(firstHandler.nextHandler).isSameInstanceAs(secondHandler)
        }
    }

    @Test
    fun `test should chain multiple handlers`() {
        val first = StubRequestHandler()
        val second = StubRequestHandler()
        val third = StubRequestHandler()
        val fourth = StubRequestHandler()

        val root = RequestHandler.chained(first, second, third, fourth)

        expect {
            that(root).isSameInstanceAs(first)
            that(first.nextHandler).isSameInstanceAs(second)
            that(second.nextHandler).isSameInstanceAs(third)
            that(third.nextHandler).isSameInstanceAs(fourth)
        }
    }

    @Test
    fun `test should throw exception when chained with itself`() {
        val rootHandler: RequestHandler = spyk { }

        expectThrows<CircularChainingException> { rootHandler.chain(rootHandler) }
    }
}