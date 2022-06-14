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

import com.exactpro.th2.act.core.handlers.IRequestHandler
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.common.grpc.*
import io.grpc.Context
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS

class ActionFactory(
    private val messageRouter: MessageRouter,
    private val eventRouter: EventRouter,
    private val subscriptionManager: SubscriptionManager,
    private val handler: IRequestHandler
) {
    private lateinit var requestContext: RequestContext

    fun createAction(
        rpcName: String,
        requestName: String,
        parentEventID: EventID,
        timeout: Long = Context.current().deadline.timeRemaining(MILLISECONDS),
        preFilter: ((Message) -> Boolean) = { true }
    ): Action {

        requestContext = RequestContext(
            rpcName,
            requestName,
            messageRouter,
            eventRouter,
            parentEventID,
            Checkpoint.getDefaultInstance(),
            subscriptionManager = subscriptionManager,
            timeout = timeout
        )

        val responder = Responder()

        val receiverFactory = MessageReceiverFactory(subscriptionManager, parentEventID, preFilter)
        val responseReceiver = ResponseReceiver(handler, receiverFactory)

        return Action(requestContext, responder, responseReceiver)
    }

    infix fun Action.`do`(action: Action.() -> Unit) {
        action.invoke(this).run {  }
    }
}