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
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import mu.KLogger

class UnhandledExceptionLogger(
    requestHandler: IRequestHandler, private val logger: KLogger
): AbstractRequestHandlerDecorator(requestHandler) {

    override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {
        try {
            super.handle(request, responder, requestContext)
        } catch (e: Throwable) {
            val error = "An unhandled exception occurred while processing the ${requestContext.requestName} " +
                        "for ${requestContext.rpcName}."

            logger.error(e) { "$error\nThe Request: ${request.debugString}" }
            responder.onError(error)
        }
    }
}
