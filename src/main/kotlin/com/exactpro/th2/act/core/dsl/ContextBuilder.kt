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
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.sessionAlias

fun context(
    handler: IRequestHandler,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID
): Context {
    val subscriptionManager = SubscriptionManager()
    return Context(handler, subscriptionManager, messageRouter, eventRouter, parentEventID)
}

fun context(
    handler: IRequestHandler,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID,
    preFilter: ((Message) -> Boolean)
): Context {

    val subscriptionManager = subscriptionManager(preFilter)
    return Context(handler, subscriptionManager, messageRouter, eventRouter, parentEventID)
}

fun context(
    handler: IRequestHandler,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID,
    connectivity: Map<String, Direction>
): Context {

    val sessionAliasList = listOf<String>().plus(connectivity.keys.toTypedArray())
    val directionList = listOf<Direction>().plus(connectivity.values.toTypedArray())
    val subscriptionManager = subscriptionManager(sessionAliasList, directionList)

    return Context(handler, subscriptionManager, messageRouter, eventRouter, parentEventID)
}

fun context(
    handler: IRequestHandler,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID,
    sessionAlias: String,
    direction: Direction,
    anotherSessionAlias: String
): Context {
    val sessionAliasList = listOf(sessionAlias, anotherSessionAlias)
    val directionList = listOf(direction)
    val subscriptionManager = subscriptionManager(sessionAliasList, directionList)
    return Context(handler, subscriptionManager, messageRouter, eventRouter, parentEventID)
}

infix fun Context.`do`(action: Context.() -> Unit) {
    action.invoke(this)
}

fun subscriptionManager(sessionAliasList: List<String>, directionList: List<Direction>): SubscriptionManager{
    return SubscriptionManager {msg: Message -> sessionAliasList.contains(msg.sessionAlias) && directionList.contains(msg.direction)}
}

fun subscriptionManager(preFilter: ((Message) -> Boolean)): SubscriptionManager{
    return SubscriptionManager(preFilter)
}