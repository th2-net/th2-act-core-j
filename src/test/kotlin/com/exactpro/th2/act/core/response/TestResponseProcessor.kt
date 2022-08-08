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
import com.exactpro.th2.act.core.messages.MessageMatches
import com.exactpro.th2.act.core.action.NoResponseFoundException
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.common.event.Event.Status
import com.exactpro.th2.common.grpc.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.assertions.*

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
    fun createRequestContext(eventRouter: EventRouter): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = mockk { },
        eventBatchRouter = eventRouter,
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance(),
        subscriptionManager = SubscriptionManager(),
        1_000
    )

    lateinit var eventRouter: EventRouter
    lateinit var requestContext: RequestContext

    @BeforeEach
    internal fun setUp() {
        eventRouter = mockk()
        requestContext = createRequestContext(eventRouter)
    }

    @Test
    fun `test should create a response processor`() {
        ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE.typeName())),
            description = randomString()
        )
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.Status::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should submit a response received event`(requestStatus: RequestStatus.Status) {
        val description = randomString()
        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE.typeName())),
            description = description
        )

        val processedMessages = randomMessage()
        every { eventRouter.createResponseReceivedEvents(any(), any(), any(), any()) } answers { randomString().toEventID() }

        val messageMatches = mutableListOf(MessageMatches(processedMessages, Status.PASSED))

        responseProcessor.process(
            messagesMatches = messageMatches,
            processedMessageIDs = listOf(processedMessages.metadata.id),
            requestContext = requestContext
        )

        val responseMessagesSlot = slot<List<Message>>()

        verify {
            eventRouter.createResponseReceivedEvents(
                messages = capture(responseMessagesSlot),
                eventStatus = Status.PASSED,
                parentEventID = requestContext.parentEventID,
                description = description
            )
        }

        expect {
            that(responseMessagesSlot.captured).containsExactly(processedMessages)
        }
    }

    @Test
    fun `test should submit a no response received event`() {
        val noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE.typeName()))

        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = noResponseBodyFactory,
            description = randomString()
        )

        val processedMessages = 5 of { randomMessage() }

        every { eventRouter.createNoResponseEvent(any(), any(), any()) } answers { randomString().toEventID() }

        assertThrows(NoResponseFoundException::class.java) {
            responseProcessor.process(
                messagesMatches = emptyList(),
                processedMessageIDs = processedMessages.map { it.metadata.id },
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

            expect {
                that(processedMessageIDsSlot.captured).containsExactly(processedMessages.map { it.metadata.id })
                that(clientResponseMessagesSlot.captured).isEmpty()
            }
        }
    }

    @Test
       fun `test should submit a no matching mapping event`() {
       val receivedMessages = 5 of { randomMessage() }

       val messageMatches = mutableListOf<MessageMatches>()
       receivedMessages.forEach { messageMatches.add(MessageMatches(it, Status.FAILED)) }

        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE.typeName())),
            description = randomString()
        )

        every { eventRouter.createErrorEvent(any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            messagesMatches = messageMatches,
            processedMessageIDs = receivedMessages.map { it.metadata.id },
            requestContext = requestContext
        )

        verify {
            eventRouter.createErrorEvent(
                description = "Found a message for failOn.",
                parentEventID = requestContext.parentEventID
            )
        }
    }

    @Test
    fun `test should not send response to client`() {
        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE.typeName())),
            description = randomString()
        )

        val receivedMessage = TestMessageType.REJECT.toRandomMessage()

        every { eventRouter.createResponseReceivedEvents(any(), any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            messagesMatches = listOf(MessageMatches(randomMessage(), Status.PASSED)),
            processedMessageIDs = listOf(receivedMessage.metadata.id),
            requestContext = requestContext
        )
    }

    /* TODO: Add similar scenarios with when there's:
     * 1) No response messages.
     * 2) No matching message mapping.
     */
}