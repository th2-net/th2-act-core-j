package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.core.rules.EchoCheckRule
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.sessionAlias

fun context(messageRouter: MessageRouter,
            responseMonitor: IMessageResponseMonitor,
            subscriptionManager: SubscriptionManager,

            connectivity: Map<String, Direction>,
            preFilter: (Message) -> Unit = {},
            action: Context.() -> Unit){

    val sessionAlias = connectivity.keys.toTypedArray()
    val direction = connectivity.values.toTypedArray()

    if (sessionAlias.size > 1) {
        action.invoke(
            Context(messageRouter, responseMonitor, subscriptionManager, sessionAlias[0], direction[0], sessionAlias[1], direction[1])
        )
    }
    else {
        action.invoke(Context(messageRouter, responseMonitor, subscriptionManager, sessionAlias[0], direction[0]))
    }
}

fun context(messageRouter: MessageRouter,
            responseMonitor: IMessageResponseMonitor,
            subscriptionManager: SubscriptionManager,

            sessionAlias: String,
            anotherSessionAlias: String,
            preFilter: (Message) -> Unit = {},
            action: Context.() -> Unit){
    action.invoke(Context(messageRouter, responseMonitor, subscriptionManager, sessionAlias,  Direction.FIRST, anotherSessionAlias))
}

class Context(private val messageRouter: MessageRouter,
              private val responseMonitor: IMessageResponseMonitor,
              private val subscriptionManager: SubscriptionManager,

              private val sessionAlias: String,
              private val direction: Direction,
              private val anotherSessionAlias: String = "",
              private val anotherDirection: Direction = Direction.FIRST,

              private val preFilter: (Message) -> Unit = {}) {

    fun send(message: Message, waitEcho: Boolean = false, cleanBuffer: Boolean = true): Message { //TODO cleanBuffer
        messageRouter.sendMessage(message)

        if (waitEcho) {
            val echo = receive{
                passOn(message.metadata.messageType) {
                    this@Context.sessionAlias == sessionAlias
                            && direction == Direction.SECOND
                            && parentEventId == message.parentEventId
                }
            }
            return echo
        }
        return message
    }

    fun receive(filter: ReceiveBuilder.() -> Boolean): Message {
        val expectedMessage = Message.newBuilder().apply {
            this.sessionAlias = this@Context.sessionAlias
        }.build()

        val request = Request(expectedMessage)
        val checkRule = EchoCheckRule(request.requestMessage, request.requestMessage.parentEventId)

        val receiver = MessageReceiver(subscriptionManager, responseMonitor, checkRule, direction)

        for (msg in receiver.responseMessages){
            if (ReceiveBuilder(msg).filter()){
                return msg
            }
        }
        return Message.getDefaultInstance()
    }

    fun repeatUntil(msg: Message, until: (Message) -> Boolean): ((Message) -> Boolean)? {
        if(until.invoke(msg))
            return until
        return null
    }

    infix fun ((Message) -> Boolean)?.`do`(func: () -> Message): List<Message> {
        val messages = mutableListOf<Message>()
        do {
            val message = func.invoke()
            messages.add(message)
        } while (this != null && repeatUntil(message, this) != null)
        return messages
    }
}