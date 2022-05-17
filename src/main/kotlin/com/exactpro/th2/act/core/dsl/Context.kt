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
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

private val LOGGER = KotlinLogging.logger {}
class Context(
    private val handler: IRequestHandler,
    private val subscriptionManager: SubscriptionManager,
    messageRouter: MessageRouter,
    eventRouter: EventRouter,
    parentEventID: EventID
) {

    private var responder: Responder = Responder()
    private var requestContext: RequestContext
    private lateinit var request: Request

    private val returnMessages = mutableListOf<Message>()
    private val start = System.currentTimeMillis()

    init {
        requestContext = RequestContext(
            "",
            "",
            messageRouter,
            eventRouter,
            parentEventID,
            Checkpoint.getDefaultInstance(),
            Context.current()
        )
    }

    fun send(
        message: Message,
        sessionAlias: String,
        waitEcho: Boolean = false,
        timeout: Long = 10_000,
        cleanBuffer: Boolean = false
    ): Message {

        val requestMessageSubmitter = RequestMessageSubmitter()

        request = Request(message)

        requestMessageSubmitter.handle(request, responder, requestContext)

        if (cleanBuffer){
            responder.cleanResponseMessages()
        }

        if (waitEcho) {
            return receive(message.messageType, sessionAlias, Direction.SECOND, timeout) {
                passOn(message.metadata.messageType) {
                    parentEventId == message.parentEventId
                }
                passOn(message.metadata.messageType) {
                    parentEventId != message.parentEventId
                }
            }
        }

        return message
    }

    fun receive(
        messageType: String,
        sessionAlias: String,

        direction: Direction = Direction.SECOND,
        timeout: Long,
        filter: ReceiveBuilder.() -> Boolean
    ): Message {

        val connectionID = ConnectionID.newBuilder().setSessionAlias(sessionAlias).build()
        val requestMessage = Message.newBuilder().apply {
            this.sessionAlias = sessionAlias
            this.messageType = messageType
        }.build()

        val checkRule = CheckRule(connectionID, returnMessages)

        val receiverFactory = MessageReceiverFactory(
            subscriptionManager,
            connectionID,
            requestMessage,
            direction,
            checkRule
        )

        val messageMapping =
            listOf(MessageMapping(listOf(MessageType.getMessageType(messageType)!!), false, StatusMapping.PASSED))
        val responseProcessor = ResponseProcessor(messageMapping) { emptyList() }

        val responseReceiver = SystemResponseReceiver(handler, receiverFactory, responseProcessor, timeout)
        responseReceiver.handle(request, responder, requestContext)

        val responseMessage = responder.getResponseMessages()
        if (responseMessage.isNotEmpty()) {
            responseMessage.forEach{
                if (ReceiveBuilder(it).filter() && !returnMessages.contains(it)) {
                    returnMessages.add(it)
                    return it
                }
            }
        } else {
            LOGGER.debug("There were no messages matching this selection")
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
            if (messages.contains(message)) break
            if (this != null && repeatUntil(message, this) == null) break
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
    TRADE_CAPTURE_REPORT_ACK("TradeCaptureReportAck"),
    QUOTE_STATUS_REPORT("QuoteStatusReport"),
    DQ126("DQ126");

    override fun getTypeName() = typeName

    companion object {
        fun getMessageType(typeName: String): MessageType? {
            return values().find { it.typeName == typeName }
        }
    }
}

