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

abstract class RequestHandler: IRequestHandler {

    private var nextHandler: IRequestHandler? = null

    /**
     * Adds the specified [IRequestHandler] to the end of the chain and returns this handler.
     */
    final override fun chain(handler: IRequestHandler): IRequestHandler {

        if (handler === this) {
            throw CircularChainingException("You cannot chain a handler with itself.")
        }

        nextHandler?.also {
            it.chain(handler)
        } ?: run {
            nextHandler = handler
        }

        return this
    }

    /**
     * Passes the received [IRequest], and [RequestContext] to the next handler in the chain.
     */
    protected fun toNextHandler(request: IRequest, requestContext: RequestContext) {
        nextHandler?.handle(request, requestContext)
    }

    companion object {
        /**
         * Chains all of the specified handlers with the first handler and returns it.
         */
        @JvmStatic
        fun chained(firstHandler: IRequestHandler, vararg otherHandlers: IRequestHandler): IRequestHandler {
            otherHandlers.fold(firstHandler) { previous, current -> current.also { previous.chain(it) } }
            return firstHandler
        }
    }
}
