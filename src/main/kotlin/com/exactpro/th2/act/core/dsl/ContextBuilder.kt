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

package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.schema.factory.CommonFactory


fun context(
    commonFactory: CommonFactory,
    rpcName: String,
    requestName: String,
    parentEventID: EventID,
    timeout: Long,
    preFilter: ((Message) -> Boolean)? = null
): Context {
    val messageRouter = MessageRouter(commonFactory.messageRouterParsedBatch)

    val eventRouter = EventRouter(commonFactory.eventBatchRouter)

    val checkpoint = Checkpoint.getDefaultInstance()
    val current = io.grpc.Context.current()

    val requestContext = RequestContext(
        rpcName,
        requestName,
        messageRouter,
        eventRouter,
        parentEventID,
        checkpoint,
        current
    )

    val responder = Responder()
    if (preFilter != null) responder.addPreFilter(preFilter)

    return Context(requestContext, responder, timeout)
}

infix fun Context.`do`(action: Context.() -> Unit) {
    action.invoke(this)
}