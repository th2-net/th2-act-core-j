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
import com.exactpro.th2.act.core.handlers.RequestMessageSubmitter
import com.exactpro.th2.act.core.handlers.decorators.SystemResponseReceiver
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.act.core.messages.MessageMapping
import com.exactpro.th2.act.core.messages.StatusMapping
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.ResponseProcessor
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sessionAlias
import io.grpc.Context
import mu.KotlinLogging


private val LOGGER = KotlinLogging.logger {}
class Context(
    private val handler: IRequestHandler,
    private val subscriptionManager: SubscriptionManager,
    private var messageRouter: MessageRouter,
    private var eventRouter: EventRouter,
) {

    private var responder: Responder = Responder()
    private lateinit var requestContext: RequestContext
    private lateinit var request: Request

    fun send(
        message: Message,
        sessionAlias: String,
        direction: Direction = Direction.FIRST,
        waitEcho: Boolean = false,
        cleanBuffer: Boolean = true
    ): Message { //TODO cleanBuffer

        val requestMessageSubmitter = RequestMessageSubmitter()

        requestContext = RequestContext(
            "",
            "",
            messageRouter,
            eventRouter,
            message.parentEventId,
            Checkpoint.getDefaultInstance(),
            Context.current()
        )

        request = Request(message)

        requestMessageSubmitter.handle(request, responder, requestContext)

        if (waitEcho) {
            val echo = receive(message.messageType, message.parentEventId, sessionAlias, Direction.SECOND) {
                passOn(message.metadata.messageType) {
                    parentEventId == message.parentEventId
                }
            }
            return echo
        }
        return message
    }

    fun receive(
        messageType: String,
        eventID: EventID,

        sessionAlias: String,
        direction: Direction = Direction.SECOND,
        filter: ReceiveBuilder.() -> Boolean
    ): Message {

        val connectionID = ConnectionID.newBuilder().setSessionAlias(sessionAlias).build()
        val requestMessage = Message.newBuilder().apply {
            this.sessionAlias = sessionAlias
            this.parentEventId = eventID
            this.messageType = messageType
        }.build()

        val checkRule = CheckRule(connectionID) //TODO

        val receiverFactory = MessageReceiverFactory(
            subscriptionManager,
            connectionID,
            requestMessage,
            direction,
            checkRule
        )

        val messageMapping =
            listOf(MessageMapping(listOf(MessageType.getMessageType(messageType)!!), true, StatusMapping.PASSED))
        val responseProcessor = ResponseProcessor(messageMapping) { emptyList() }

        val responseReceiver = SystemResponseReceiver(handler, receiverFactory, responseProcessor)
        responseReceiver.handle(request, responder, requestContext)

        val responseMessage = responder.responseMessages[0]

        return if (ReceiveBuilder(responseMessage).filter()) { //TODO
            responseMessage
        } else {
            LOGGER.debug("There were no messages matching this selection")
            Message.getDefaultInstance()
        }
    }

    fun repeatUntil(msg: Message? = null, until: (Message) -> Boolean): ((Message) -> Boolean)? {
        if (msg == null || until.invoke(msg)) return until
        return null
    }

    infix fun ((Message) -> Boolean)?.`do`(func: () -> Message): List<Message> {
        val messages = mutableListOf<Message>()
        do {
            val message = func.invoke()
            if (messages.contains(message)) break
            messages.add(message)
        } while (this != null && repeatUntil(message, this) != null)
        return messages
    }
}


enum class MessageType(private val typeName: String) : IMessageType {

    NEW_ORDER_SINGLE("NewOrderSingle"),
    ORDER_CANCEL_REPLACE_REQUEST("OrderCancelReplaceRequest"),
    ORDER_CANCEL_REQUEST("OrderCancelRequest"),
    EXECUTION_REPORT("ExecutionReport"),
    BUSINESS_MESSAGE_REJECT("BusinessMessageReject"),
    REJECT("Reject"),
    ORDER_CANCEL_REJECT("OrderCancelReject"),
    TRADE_CAPTURE_REPORT("TradeCaptureReport"),
    TRADE_CAPTURE_REPORT_ACK("TradeCaptureReportAck");

    override fun getTypeName() = typeName

    companion object {
        fun getMessageType(typeName: String): MessageType? {
            return values().find { it.typeName == typeName }
        }
    }
}

