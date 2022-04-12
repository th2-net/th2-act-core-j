package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.core.rules.EchoCheckRule
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.sessionAlias

fun context(
    messageRouter: MessageRouter,
    responseMonitor: IMessageResponseMonitor,
    subscriptionManager: SubscriptionManager,

    connectivity: Map<String, Direction>,
    preFilter: ((Message) -> Boolean)? = null): Context{

    val sessionAlias = connectivity.keys.toTypedArray()
    val direction = connectivity.values.toTypedArray()

    val messageReceiver = cacheFiltering(responseMonitor, subscriptionManager, sessionAlias[0], direction[0], preFilter)

    return if (sessionAlias.size > 1) {
        Context(messageRouter, messageReceiver, sessionAlias[0], direction[0], sessionAlias[1], direction[1])
    }
    else {
        Context(messageRouter, messageReceiver, sessionAlias[0], direction[0])
    }
}

fun context(
    messageRouter: MessageRouter,
    responseMonitor: IMessageResponseMonitor,
    subscriptionManager: SubscriptionManager,

    sessionAlias: String,
    direction: Direction,
    anotherSessionAlias: String,
    preFilter: ((Message) -> Boolean)? = null): Context{

    val messageReceiver = cacheFiltering(responseMonitor, subscriptionManager, sessionAlias, direction, preFilter)
    return  Context(messageRouter, messageReceiver, sessionAlias, direction, anotherSessionAlias)
}

fun cacheFiltering(responseMonitor: IMessageResponseMonitor,
                   subscriptionManager: SubscriptionManager,

                   sessionAlias: String,
                   direction: Direction,
                   preFilter: ((Message) -> Boolean)?): MessageReceiver {
    val expectedMessage = Message.newBuilder().apply {
        this.sessionAlias = sessionAlias
    }.build()

    val request = Request(expectedMessage)
    val checkRule = EchoCheckRule(request.requestMessage, request.requestMessage.parentEventId, preFilter)

    return MessageReceiver(subscriptionManager, responseMonitor, checkRule, direction)
}

infix fun Context.`do`(action: Context.() -> Unit) {
    action.invoke(this)
}

