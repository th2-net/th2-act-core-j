package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.core.rules.EchoCheckRule
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sessionAlias
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

fun context(messageRouter: MessageRouter, responseMonitor: IMessageResponseMonitor, subscriptionManager: SubscriptionManager, action: Context.() -> Unit)/*: ResultBuilder*/{
    action.invoke(Context(messageRouter, responseMonitor, subscriptionManager))
}

class Context (private val messageRouter: MessageRouter, private val responseMonitor: IMessageResponseMonitor, private val subscriptionManager: SubscriptionManager) { //??

    fun send(message: Message, waitEcho: Boolean = false, cleanBuffer: Boolean = true): Message { //TODO cleanBuffer
        val sendMessage = messageRouter.sendMessage(message)

        if (waitEcho) {
            LOGGER.debug("Started  waiting echo.")
            val echo = receive(message.sessionAlias, Direction.SECOND) { msg ->
                passOn(msg, message.messageType) {
                    msg.direction == Direction.SECOND
                            && msg.parentEventId == message.parentEventId
                }
            }
            return echo
        }

        return message
    }

    fun receive(sessionAlias: String, direction: Direction, filter: ReceiveBuilder.(Message) -> ReceiveBuilder.Status): Message {
        LOGGER.debug("Started  receive message.")

        val expectedMessage = Message.newBuilder().apply{
            this.sessionAlias = sessionAlias
            this.direction = direction
            this.messageType = "NewOrderSingle"
        }.build()

        val request = Request(expectedMessage)
        val checkRule = EchoCheckRule(request.requestMessage, request.requestMessage.parentEventId)

        val receiver = MessageReceiver(subscriptionManager, responseMonitor, checkRule, direction)

        for (msg in receiver.responseMessages){
            if (filter.invoke(ReceiveBuilder(), msg) == ReceiveBuilder.Status.PASSED){
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
        var message: Message
        do {
            message = func.invoke()
            messages.add(message)
        } while (this != null && repeatUntil(message, this) != null)
        return messages
    }
}