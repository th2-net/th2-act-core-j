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
import com.exactpro.th2.act.core.messages.MessageMapping
import com.exactpro.th2.act.core.messages.StatusMapping
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.NoResponseBodyFactory
import com.exactpro.th2.act.core.response.ResponseProcessor
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.messageType
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class Context(
    private val handler: IRequestHandler,
    private val subscriptionManager: SubscriptionManager,
    private val requestContext: RequestContext
) {

    private var responder: Responder = Responder()
    private lateinit var request: Request

    fun send(
        message: Message,
        sessionAlias: String,
        waitEcho: Boolean = false,
        timeout: Long = 10_000,
        cleanBuffer: Boolean = false
    ): Message {
        request = Request(message)

        val requestMessageSubmitter = RequestMessageSubmitter()
        requestMessageSubmitter.handle(request, responder, requestContext)

        if (cleanBuffer) responder.cleanResponseMessages()

        return if (waitEcho) {
            receive(message.messageType, sessionAlias, Direction.SECOND, timeout) {
                passOn(message.metadata.messageType) {
                    parentEventId == message.parentEventId
                }
                failOn(message.metadata.messageType) {
                    parentEventId != message.parentEventId
                }
            }
        } else message
    }

    fun receive(
        messageType: String,
        sessionAlias: String,
        direction: Direction = Direction.SECOND,
        timeout: Long,
        filter: ReceiveBuilder.() -> Boolean
    ): Message {
        val connectionID = ConnectionID.newBuilder().setSessionAlias(sessionAlias).build()
        val checkRule = CheckRule(connectionID, responder.getResponseMessages())
        val receiverFactory =
            MessageReceiverFactory(subscriptionManager, connectionID, request.requestMessage, direction, checkRule)
        val responseProcessor: ResponseProcessor = responseProcessor(MessageType.getMessageType(messageType))

        val responseReceiver = SystemResponseReceiver(handler, receiverFactory, responseProcessor, timeout)
        responseReceiver.handle(request, responder, requestContext)

        val lastIndex = responder.getResponseMessages().lastIndex
        if (lastIndex >= 0) {
            val responseMessage = responder.getResponseMessages()[lastIndex]
            if (ReceiveBuilder(responseMessage).filter()) return responseMessage
        } else LOGGER.debug("There were no messages matching this selection")

        return Message.getDefaultInstance()
    }

    fun repeatUntil(msg: Message? = null, until: (Message) -> Boolean): ((Message) -> Boolean)? =
        if (msg == null || until.invoke(msg)) until
        else null

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

    private val responseProcessor: (MessageType?) -> ResponseProcessor = { msgType ->
        val messageTypes = mutableListOf<MessageType>()
        val noResponseBodyFactory = null
        if (msgType != null) {
            messageTypes.add(msgType)
            NoResponseBodyFactory(msgType)
        }
        ResponseProcessor(listOf(MessageMapping(messageTypes, false, StatusMapping.PASSED))) {
            noResponseBodyFactory ?: emptyList()
        }
    }
}