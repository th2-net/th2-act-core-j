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

package com.exactpro.th2.act.core.action

import com.exactpro.th2.act.core.rules.ReceiveBuilder
import com.exactpro.th2.act.core.handlers.decorators.ResponseReceiver
import com.exactpro.th2.act.core.handlers.RequestMessageSubmitter
import com.exactpro.th2.act.core.messages.MessageMapping
import com.exactpro.th2.act.core.messages.StatusMapping
import com.exactpro.th2.act.core.monitors.CountResponseCollector
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.NoResponseBodyFactory
import com.exactpro.th2.act.core.response.ResponseProcessor
import com.exactpro.th2.act.core.rules.ReceiveRule
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import io.grpc.stub.StreamObserver
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class Action<T>(
    private val observer: StreamObserver<T>,
    private val requestContext: RequestContext,
    private val responseReceiver: ResponseReceiver
) {
    private val requestMessageSubmitter = RequestMessageSubmitter()
    private val seqNumPreviousMessage: MutableMap<Map<String, Direction>, Long> = mutableMapOf()

    fun send(
        message: Message,
        sessionAlias: String = message.sessionAlias,
        timeout: Long,
        waitEcho: Boolean = false,
        cleanBuffer: Boolean = true
    ): Message {
        checkingContext()

        if (cleanBuffer) {
            responseReceiver.cleanMessageBuffer()
            seqNumPreviousMessage.clear()
        }

        val request = Request(message)
        requestMessageSubmitter.handle(request, requestContext)

        return if (waitEcho) {
            receive(timeout, sessionAlias, Direction.SECOND) {
                passOn(message.messageType) { parentEventId == message.parentEventId }
            }
        } else message
    }

    fun receive(
        timeout: Long,
        sessionAlias: String,
        direction: Direction = Direction.SECOND,
        filter: ReceiveBuilder.() -> ReceiveBuilder
    ): Message {
        checkingContext()

        val messageTypes = ReceiveBuilder(Message.getDefaultInstance()).invoke(filter).messageTypes()

        val responseProcessor = ResponseProcessor(
            listOf(MessageMapping(messageTypes, false, StatusMapping.PASSED)),
            NoResponseBodyFactory(messageTypes[0])
        )

        val remainingTime = requestContext.remainingTime
        val deadline: Long =
            if (timeout < remainingTime) timeout
            else {
                LOGGER.debug { "The timeout for receive exceeds the remaining time. A timeout of $remainingTime is used." }
                remainingTime
            }

        if (seqNumPreviousMessage[mapOf(sessionAlias to direction)] == null)
            seqNumPreviousMessage[mapOf(sessionAlias to direction)] = Long.MIN_VALUE

        val receiveRule = ReceiveRule(filter) {
                msg: Message -> msg.sessionAlias == sessionAlias
                && msg.direction == direction
                && msg.sequence > seqNumPreviousMessage[mapOf(sessionAlias to direction)]!!
        }

        val collector = CountResponseCollector.singleResponse()
        responseReceiver.handle(requestContext, responseProcessor, deadline, receiveRule, collector)

        val responseMessage = collector.responses.single() // TODO: check that we really have only one message in responses
        seqNumPreviousMessage[mapOf(sessionAlias to direction)] = responseMessage.sequence
        return responseMessage
    }

    fun repeat(func: () -> Message): () -> Message = func

    infix fun (() -> Message).until(until: (Message) -> Boolean): List<Message> {
        val messages = mutableListOf<Message>()
        do {
            val msg = this.invoke()
            messages.add(msg)
        } while (until.invoke(msg))

        return messages
    }

    private fun checkingContext() {
        if (requestContext.isOverDeadline) {
            throw RuntimeException("Timeout = ${requestContext.timeout} ms ended before context execution was completed")
        }
    }

    fun emitResult(result: T) {
        observer.onNext(result)
    }
}