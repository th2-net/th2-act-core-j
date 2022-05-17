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
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.sessionAlias

fun context(
    rpcName: String,
    requestName: String,
    handler: IRequestHandler,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID
): Context = Context(
    handler,
    SubscriptionManager(),
    RequestContext(
        rpcName,
        requestName,
        messageRouter,
        eventRouter,
        parentEventID,
        Checkpoint.getDefaultInstance(),
        io.grpc.Context.current()
    )
)

fun context(
    handler: IRequestHandler,
    rpcName: String,
    requestName: String,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID,
    preFilter: ((Message) -> Boolean)
): Context = Context(
    handler,
    subscriptionManager(preFilter),
    RequestContext(
        rpcName,
        requestName,
        messageRouter,
        eventRouter,
        parentEventID,
        Checkpoint.getDefaultInstance(),
        io.grpc.Context.current()
    )
)

fun context(
    handler: IRequestHandler,
    rpcName: String,
    requestName: String,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID,
    connectivity: Map<String, Direction>
): Context = Context(
    handler,
    subscriptionManager(
        listOf<String>().plus(connectivity.keys.toTypedArray()),
        listOf<Direction>().plus(connectivity.values.toTypedArray())
    ),
    RequestContext(
        rpcName,
        requestName,
        messageRouter,
        eventRouter,
        parentEventID,
        Checkpoint.getDefaultInstance(),
        io.grpc.Context.current()
    )
)

fun context(
    handler: IRequestHandler,
    rpcName: String,
    requestName: String,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID,
    sessionAlias: String,
    direction: Direction,
    anotherSessionAlias: String
): Context = Context(
    handler,
    subscriptionManager(listOf(sessionAlias, anotherSessionAlias), listOf(direction)),
    RequestContext(
        rpcName,
        requestName,
        messageRouter,
        eventRouter,
        parentEventID,
        Checkpoint.getDefaultInstance(),
        io.grpc.Context.current()
    )
)

infix fun Context.`do`(action: Context.() -> Unit) {
    action.invoke(this)
}

fun subscriptionManager(sessionAliasList: List<String>, directionList: List<Direction>): SubscriptionManager =
    SubscriptionManager { msg: Message ->
        sessionAliasList.contains(msg.sessionAlias) && directionList.contains(msg.direction)
    }

fun subscriptionManager(preFilter: ((Message) -> Boolean)): SubscriptionManager = SubscriptionManager(preFilter)