package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.sessionAlias

class Context{
    private lateinit var messageRouter: MessageRouter
    private lateinit var messageReceiver: MessageReceiver
    private lateinit var sessionAlias: String
    private lateinit var direction: Direction
    private lateinit var anotherSessionAlias: String
    private lateinit var anotherDirection: Direction

    constructor(messageRouter: MessageRouter,
                messageReceiver: MessageReceiver,

                sessionAlias: String,
                direction: Direction){
        initialize(messageRouter, messageReceiver, sessionAlias, direction)
    }

    constructor(messageRouter: MessageRouter,
                messageReceiver: MessageReceiver,

                sessionAlias: String,
                direction: Direction,
                anotherSessionAlias: String,
                anotherDirection: Direction){
        initialize(messageRouter, messageReceiver, sessionAlias, direction)
        this.anotherSessionAlias = anotherSessionAlias
        this.anotherDirection = anotherDirection
    }

    constructor(messageRouter: MessageRouter,
                messageReceiver: MessageReceiver,

                sessionAlias: String,
                direction: Direction,
                anotherSessionAlias: String){
        initialize(messageRouter, messageReceiver, sessionAlias, direction,)
        this.anotherSessionAlias = anotherSessionAlias
    }

    private fun initialize(messageRouter: MessageRouter,
                           messageReceiver: MessageReceiver,

                           sessionAlias: String,
                           direction: Direction){
        this.messageRouter = messageRouter
        this.messageReceiver = messageReceiver
        this.sessionAlias = sessionAlias
        this.direction = direction
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
        val responseMessages = messageReceiver.responseMessages

        for (msg in responseMessages){
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