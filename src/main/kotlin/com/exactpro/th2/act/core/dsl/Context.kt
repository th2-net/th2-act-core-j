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
import com.exactpro.th2.act.core.managers.ISubscriptionManager
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.act.core.messages.MessageMapping
import com.exactpro.th2.act.core.messages.StatusMapping
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.NoResponseBodyFactory
import com.exactpro.th2.act.core.response.ResponseProcessor
import com.exactpro.th2.act.grpc.ActGrpc.ActImplBase
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.messageType
import io.grpc.Deadline
import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

class Context(
    private val requestContext: RequestContext,
    private val responder: Responder,
    private val timeout: Long,
    private val handler: IRequestHandler = Handler(),
    private val subscriptionManager: ISubscriptionManager = SubscriptionManager(),
): ActImplBase() {

    private lateinit var request: Request
    private val currentContext = io.grpc.Context.current()
    private var blockingStub = currentContext.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS, Executors.newSingleThreadScheduledExecutor())

    fun send(
        message: Message,
        sessionAlias: String,
        waitEcho: Boolean = false,
        cleanBuffer: Boolean = false
    ): Message {
        checkingContext()

        request = Request(message)

        val requestMessageSubmitter = RequestMessageSubmitter()
        requestMessageSubmitter.handle(request, responder, requestContext)

        if (cleanBuffer) responder.cleanResponseMessages()

        return if (waitEcho) {
            receive(message.messageType, sessionAlias, Direction.SECOND) {
                failOn(message.messageType) {
                    parentEventId != message.parentEventId
                }
            } ?: message
        } else message
    }

    fun receive(
        messageType: String,
        sessionAlias: String,
        direction: Direction = Direction.SECOND,
        filter: ReceiveBuilder.() -> Boolean
    ): Message? {
        checkingContext()

        val connectionID = ConnectionID.newBuilder().setSessionAlias(sessionAlias).build()
        val checkRule = CheckRule(connectionID, responder.getResponseMessages())
        val receiverFactory =
            MessageReceiverFactory(subscriptionManager, connectionID, request.requestMessage, direction, checkRule)
        val msgType = IMessageType { messageType }
        val responseProcessor = ResponseProcessor(listOf(MessageMapping(
            messageTypes = listOf(msgType),
            checkExactMatch = false,
            statusMapping = StatusMapping.PASSED
        )), NoResponseBodyFactory(msgType))

        val responseReceiver = SystemResponseReceiver(
            handler, receiverFactory, responseProcessor,
            getDeadline().timeRemaining(TimeUnit.MILLISECONDS)
        )
        responseReceiver.handle(request, responder, requestContext)

        val lastIndex = responder.getResponseMessages().lastIndex
        if (lastIndex >= 0) {
            val responseMessage = responder.getResponseMessages()[lastIndex]
            if (ReceiveBuilder(responseMessage).filter()) return responseMessage
        } else LOGGER.debug("There were no messages matching this selection")

        return null
    }

    fun repeat(func: () -> Message?): () -> Message? = func

    infix fun (() -> Message?).until(until: (Message) -> Boolean): List<Message> {
        val messages = mutableListOf<Message>()
        var msg = this.invoke()
        while (msg != null && until.invoke(msg)) {
            messages.add(msg)
            msg = this.invoke()
        }
        return messages
    }

    private fun checkingContext(){
        if (currentContext.isCancelled) {
            throw Exception("Cancelled by client")
        }
        if (getDeadline().isExpired) {
            throw Exception("timeout = $timeout ended before context execution was completed")
        }
    }

    private fun getDeadline(): Deadline{
        val deadline = blockingStub.deadline
        if (deadline != null) return deadline
        else throw Exception("deadline must not be null")
    }
}