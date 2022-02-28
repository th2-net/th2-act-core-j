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

package com.exactpro.th2.act.core.handlers;

import com.exactpro.th2.act.core.requests.IRequest;
import com.exactpro.th2.act.core.requests.RequestContext;
import com.exactpro.th2.act.core.response.IResponder;

public interface IRequestHandler {
    /**
     * Chains the specified request handler with this one.
     *
     * @param handler The {@link IRequestHandler} to be added to the chain.
     * @return This {@link IRequestHandler} for the convenience of a flowing interface.
     */
    IRequestHandler chain(IRequestHandler handler);

    /**
     * Handles the specified request and delegate it to the next handler in the chain. If an error occurs the
     * handler may choose to not propagate the request further.
     *
     * @param request        The request to be handled as a {@link IRequest}.
     * @param responder      The responder for returning a response to the client as a {@link IResponder}.
     * @param requestContext The {@link RequestContext} for handling the request.
     */
    void handle(IRequest request, IResponder responder, RequestContext requestContext);
}
