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

package com.exactpro.th2.act.core.response

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.messages.failedOn
import com.exactpro.th2.act.core.messages.passedOn
import com.exactpro.th2.act.core.messages.passedOnExact
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RequestStatus
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty

internal class TestResponseProcessor {

    /* Test List for ResponseProcessor:
     * 1) Should create a ResponseProcessor from a list of expected MessageMappings and an IBodyDataFactory. V
     * 2) Should submit a response received event when the expected messages are received. V
     * 3) Should submit a no response received event when the expected messages are not received. V
     * 4) Should submit a no matching mapping event when none of the expected mappings match the response messages. V
     * 5) Should include found response messages in response to client. V
     * 6) Should not attempt to respond to client if a response has already been sent. ?
     */

    /**
     * Creates a [RequestContext] from the specified event batch router.
     */
    private fun createRequestContext(eventRouter: EventRouter): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = mockk { },
        eventBatchRouter = eventRouter,
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance()
    )

    private lateinit var responder: IResponder
    private lateinit var eventRouter: EventRouter
    private lateinit var requestContext: RequestContext

    @BeforeEach
    internal fun setUp() {
        responder = mockk { }
        eventRouter = mockk { }

        requestContext = createRequestContext(eventRouter)
    }

    @Test
    fun `test should create a response processor`() {
        ResponseProcessor(
            expectedMessages = listOf(
                passedOn(TestMessageType.NEW_ORDER_SINGLE, TestMessageType.EXECUTION_REPORT),
                failedOn(TestMessageType.REJECT)
            ),
            noResponseBodyFactory = NoResponseBodyFactory(TestMessageType.NEW_ORDER_SINGLE)
        )
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.Status::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should submit a response received event`(requestStatus: RequestStatus.Status) {
        val messageMapping = when (requestStatus) {
            RequestStatus.Status.ERROR -> failedOn(TestMessageType.REJECT)
            RequestStatus.Status.SUCCESS -> passedOn(TestMessageType.REJECT)
            else -> throw RuntimeException("An invalid Enum parameter was provided.")
        }

        val responseProcessor = ResponseProcessor(
            expectedMessages = listOf(messageMapping),
            noResponseBodyFactory = NoResponseBodyFactory(TestMessageType.NEW_ORDER_SINGLE)
        )

        val processedMessages = 5 of { randomMessage() }
        val receivedMessage = TestMessageType.REJECT.toRandomMessage()

        every { responder.isResponseSent } returns false andThen true
        every { responder.onResponseFound(any(), any(), any()) } just Runs
        every { eventRouter.createResponseReceivedEvents(any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            responseMessages = listOf(receivedMessage),
            processedMessageIDs = processedMessages.plus(receivedMessage).map { it.metadata.id },
            responder = responder,
            requestContext = requestContext
        )

        val responseMessagesSlot = slot<List<Message>>()
        val clientResponseMessagesSlot = slot<List<Message>>()

        verify {
            eventRouter.createResponseReceivedEvents(
                messages = capture(responseMessagesSlot),
                eventStatus = messageMapping.statusMapping.eventStatus,
                parentEventID = requestContext.parentEventID
            )
        }
        verify {
            responder.onResponseFound(
                requestStatus, requestContext.checkpoint, capture(clientResponseMessagesSlot)
            )
        }

        expect {
            that(responseMessagesSlot.captured).containsExactly(receivedMessage)
            that(clientResponseMessagesSlot.captured).containsExactly(receivedMessage)
        }
    }

    @Test
    fun `test should submit a no response received event`() {
        val noResponseBodyFactory = NoResponseBodyFactory(TestMessageType.NEW_ORDER_SINGLE)

        val responseProcessor = ResponseProcessor(
            expectedMessages = listOf(passedOnExact(TestMessageType.NEW_ORDER_SINGLE, TestMessageType.EXECUTION_REPORT)),
            noResponseBodyFactory = noResponseBodyFactory
        )

        val processedMessages = 5 of { randomMessage() }

        every { responder.isResponseSent } returns false andThen true
        every { responder.onResponseFound(any(), any(), any()) } just Runs
        every { eventRouter.createNoResponseEvent(any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            responseMessages = emptyList(),
            processedMessageIDs = processedMessages.map { it.metadata.id },
            responder = responder,
            requestContext = requestContext
        )

        val processedMessageIDsSlot = slot<List<MessageID>>()
        val clientResponseMessagesSlot = slot<List<Message>>()

        verify {
            eventRouter.createNoResponseEvent(
                noResponseBodyFactory = noResponseBodyFactory,
                processedMessageIDs = capture(processedMessageIDsSlot),
                parentEventID = requestContext.parentEventID
            )
        }
        verify {
            responder.onResponseFound(
                RequestStatus.Status.ERROR, requestContext.checkpoint, capture(clientResponseMessagesSlot)
            )
        }

        expect {
            that(processedMessageIDsSlot.captured).containsExactly(processedMessages.map { it.metadata.id })
            that(clientResponseMessagesSlot.captured).isEmpty()
        }
    }

    @Test
    fun `test should submit a no matching mapping event`() {
        val expectedMappings = listOf(passedOnExact(TestMessageType.NEW_ORDER_SINGLE, TestMessageType.EXECUTION_REPORT))

        val responseProcessor = ResponseProcessor(
            expectedMessages = expectedMappings,
            noResponseBodyFactory = NoResponseBodyFactory(TestMessageType.NEW_ORDER_SINGLE)
        )

        val processedMessages = 5 of { randomMessage() }
        val receivedMessages = 5 of { randomMessage() }

        every { responder.isResponseSent } returns false andThen true
        every { responder.onResponseFound(any(), any(), any()) } just Runs
        every { eventRouter.createNoMappingEvent(any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            responseMessages = receivedMessages.toList(),
            processedMessageIDs = processedMessages.plus(receivedMessages).map { it.metadata.id },
            responder = responder,
            requestContext = requestContext
        )

        val receivedMessagesSlot = slot<List<Message>>()
        val clientResponseMessagesSlot = slot<List<Message>>()

        verify {
            eventRouter.createNoMappingEvent(
                expectedMappings = expectedMappings,
                receivedMessages = capture(receivedMessagesSlot),
                parentEventID = requestContext.parentEventID
            )
        }
        verify {
            responder.onResponseFound(
                RequestStatus.Status.ERROR, requestContext.checkpoint, capture(clientResponseMessagesSlot)
            )
        }

        expect { // NOTE: Stirkt bug with comparing elements from arrays to elements from a list.
            that(receivedMessagesSlot.captured).containsExactlyInAnyOrder(receivedMessages.toList())
            that(clientResponseMessagesSlot.captured).containsExactlyInAnyOrder(receivedMessages.toList())
        }
    }

    @Test
    fun `test should not send response to client`() {
        val responseProcessor = ResponseProcessor(
            expectedMessages = listOf(passedOn(TestMessageType.REJECT)),
            noResponseBodyFactory = NoResponseBodyFactory(TestMessageType.NEW_ORDER_SINGLE)
        )

        val receivedMessage = TestMessageType.REJECT.toRandomMessage()

        every { responder.isResponseSent } returns true
        every { eventRouter.createResponseReceivedEvents(any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            listOf(receivedMessage),
            processedMessageIDs = listOf(receivedMessage.metadata.id),
            responder = responder,
            requestContext = requestContext
        )

        verify(exactly = 0) { responder.onResponseFound(any(), any(), any()) }
        verify(exactly = 0) { responder.onError(any()) }
    }

    /* TODO: Add similar scenarios with when there's:
     * 1) No response messages.
     * 2) No matching message mapping.
     */
}
