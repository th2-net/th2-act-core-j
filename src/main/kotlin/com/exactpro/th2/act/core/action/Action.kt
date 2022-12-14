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

import com.exactpro.th2.act.core.rules.filter.MessageTypeCollector
import com.exactpro.th2.act.core.handlers.decorators.ResponseReceiver
import com.exactpro.th2.act.core.handlers.RequestMessageSubmitter
import com.exactpro.th2.act.core.monitors.CountResponseCollector
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.NoResponseBodyFactory
import com.exactpro.th2.act.core.response.ResponseProcessor
import com.exactpro.th2.act.core.rules.ReceiveRule
import com.exactpro.th2.act.core.rules.filter.IReceiveBuilder
import com.exactpro.th2.check1.grpc.Check1Service
import com.exactpro.th2.check1.grpc.CheckpointRequest
import com.exactpro.th2.check1.grpc.CheckpointResponse
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import java.time.Instant

private val LOGGER = KotlinLogging.logger {}

class Action<T>(
    private val observer: StreamObserver<T>,
    private val check1Service: Check1Service,
    private val requestContext: RequestContext,
    private val responseReceiver: ResponseReceiver
) {
    data class SessionKey(val sessionAlias: String, val direction: Direction)

    private val requestMessageSubmitter = RequestMessageSubmitter()
    private val sequencePreviousMessage = mutableMapOf<SessionKey, Long>()

    fun send(
        message: Message,
        sessionAlias: String = message.sessionAlias,
        timeout: Long = 1_000L,
        waitEcho: Boolean = false,
        cleanBuffer: Boolean = true,
        description: String = ""
    ): Message {
        checkingContext()
        require(message.sessionAlias == sessionAlias) {
            "session alias '${message.sessionAlias}' in the message does not match the specified alias '$sessionAlias' in the 'send' method"
        }

        if (cleanBuffer) {
            responseReceiver.cleanMessageBuffer()
            sequencePreviousMessage.clear()
        }

        val request = Request(message, description)
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
        description: String = "",
        filter: IReceiveBuilder.() -> Unit
    ): Message {
        checkingContext()

        val remainingTime = requestContext.remainingTime
        val deadline: Long =
            if (timeout < remainingTime) timeout
            else {
                LOGGER.debug { "The timeout for receive exceeds the remaining time. A timeout of $remainingTime is used." }
                remainingTime
            }

        val sessionKey = SessionKey(sessionAlias, direction)

        val sequencePrevious = sequencePreviousMessage[sessionKey] ?: Long.MIN_VALUE

        val receiveRule = ReceiveRule(filter) {
                msg: Message -> msg.sessionAlias == sessionAlias
                && msg.direction == direction
                && msg.sequence > sequencePrevious
        }
        val responseProcessor = ResponseProcessor(NoResponseBodyFactory(MessageTypeCollector().apply(filter).messageTypes), description)
        val collector = CountResponseCollector.singleResponse()
        responseReceiver.handle(requestContext, responseProcessor, deadline, receiveRule, collector)

        val responseMessage = collector.responses.single().message // TODO: check that we really have only one message in responses
        sequencePreviousMessage[sessionKey] = responseMessage.sequence
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

    fun registerCheckPoint(parentEventId: EventID?, description: String): Checkpoint {
        LOGGER.debug("Registering the checkpoint started")

        val checkpointRequest = CheckpointRequest.newBuilder()

        if (parentEventId != null) {
            val eventID = requestContext.eventBatchRouter.createParentEvent(
                parentEventId = parentEventId,
                rpcName = requestContext.rpcName,
                requestName = "${requestContext.requestName} - ${Instant.now()}",
                status = Event.Status.PASSED,
                description = description
            )

            checkpointRequest.parentEventId = eventID
        }

        val response: CheckpointResponse = check1Service.createCheckpoint(checkpointRequest.build())
        LOGGER.debug("Registering the checkpoint ended. Response $response")
        return response.checkpoint
    }
}