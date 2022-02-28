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

import com.exactpro.th2.act.core.handlers.validations.IRequestValidation
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponder
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class RequestValidator(private val validations: Collection<IRequestValidation>): RequestHandler() {
    
    override fun handle(request: IRequest, responder: IResponder, requestContext: RequestContext) {

        validations.forEach { validation ->

            val result = validation.on(request)

            if (result.isInvalid) {

                val reason = result.invalidationReason
                LOGGER.debug { "Request is invalid: $reason" }

                requestContext.eventBatchRouter.createErrorEvent(reason, requestContext.parentEventID)
                responder.onError("The sent request is invalid. Reason: $reason")
                return
            }
        }

        super.toNextHandler(request, responder, requestContext)
    }

    constructor(vararg validation: IRequestValidation): this(validation.toList())
}
