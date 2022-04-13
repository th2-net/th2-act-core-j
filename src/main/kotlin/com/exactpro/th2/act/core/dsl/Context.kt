package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.sessionAlias

class Context(
    private val messageRouter: MessageRouter,
    private val messageReceiver: MessageReceiver,
    private val sessionAlias: String,
    private val direction: Direction,
    private val anotherSessionAlias: String? = null,
    private val anotherDirection: Direction? = null
) {

    private val receiveMessage = mutableListOf<Message>()


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
            if (ReceiveBuilder(msg).filter()){ //TODO
                if (!receiveMessage.contains(msg)) {
                    receiveMessage.add(msg)
                    return msg
                }
            }
        }
        return Message.getDefaultInstance()
    }

    fun repeatUntil(msg: Message? = null, until: (Message) -> Boolean): ((Message) -> Boolean)? {
        if (msg == null || until.invoke(msg)) return until
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