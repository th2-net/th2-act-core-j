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
            Context(messageRouter, responseMonitor, subscriptionManager, sessionAlias[0], direction[0], sessionAlias[1], direction[1], preFilter)
        )
    }
    else {
        action.invoke(Context(messageRouter, responseMonitor, subscriptionManager, sessionAlias[0], direction[0], preFilter))
    }
}

fun context(messageRouter: MessageRouter,
            responseMonitor: IMessageResponseMonitor,
            subscriptionManager: SubscriptionManager,

            sessionAlias: String,
            direction: Direction,
            anotherSessionAlias: String,
            preFilter: (Message) -> Unit = {},
            action: Context.() -> Unit){
    action.invoke(Context(messageRouter, responseMonitor, subscriptionManager, sessionAlias, direction, anotherSessionAlias, preFilter))
}

class Context{
    private lateinit var messageRouter: MessageRouter
    private lateinit var responseMonitor: IMessageResponseMonitor
    private lateinit var subscriptionManager: SubscriptionManager

    private lateinit var sessionAlias: String
    private lateinit var direction: Direction
    private lateinit var anotherSessionAlias: String
    private lateinit var anotherDirection: Direction

    private lateinit var preFilter: (Message) -> Unit

    constructor(messageRouter: MessageRouter,
                responseMonitor: IMessageResponseMonitor,
                subscriptionManager: SubscriptionManager,

                sessionAlias: String,
                direction: Direction,
                preFilter: (Message) -> Unit = {}){
        initialize(messageRouter, responseMonitor, subscriptionManager, sessionAlias, direction, preFilter)
    }

    constructor(messageRouter: MessageRouter,
                responseMonitor: IMessageResponseMonitor,
                subscriptionManager: SubscriptionManager,

                sessionAlias: String,
                direction: Direction,
                anotherSessionAlias: String,
                anotherDirection: Direction,
                preFilter: (Message) -> Unit = {}){
        initialize(messageRouter, responseMonitor, subscriptionManager, sessionAlias, direction, preFilter)
        this.anotherSessionAlias = anotherSessionAlias
        this.anotherDirection = anotherDirection
    }

    constructor(messageRouter: MessageRouter,
                responseMonitor: IMessageResponseMonitor,
                subscriptionManager: SubscriptionManager,
                sessionAlias: String,
                direction: Direction,
                anotherSessionAlias: String,
                preFilter: (Message) -> Unit = {}){
        initialize(messageRouter, responseMonitor, subscriptionManager, sessionAlias, direction,  preFilter)
        this.anotherSessionAlias = anotherSessionAlias
    }

    private fun initialize(messageRouter: MessageRouter,
                           responseMonitor: IMessageResponseMonitor,
                           subscriptionManager: SubscriptionManager,
                           sessionAlias: String,
                           direction: Direction,
                           preFilter: (Message) -> Unit){
        this.messageRouter = messageRouter
        this.responseMonitor = responseMonitor
        this.subscriptionManager = subscriptionManager
        this.sessionAlias = sessionAlias
        this.direction = direction
        if(preFilter != {}){
            this.preFilter = preFilter
        }
    }

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