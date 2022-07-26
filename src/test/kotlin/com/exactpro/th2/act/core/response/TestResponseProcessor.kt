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
import com.exactpro.th2.act.core.rules.StatusReceiveBuilder
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
    private fun createRequestContext(eventRouter: EventRouter): RequestContext = RequestContext(
        rpcName = randomString(),
        requestName = randomString(),
        messageBatchRouter = mockk { },
        eventBatchRouter = eventRouter,
        parentEventID = randomString().toEventID(),
        checkpoint = Checkpoint.getDefaultInstance(),
        subscriptionManager = SubscriptionManager(),
        1_000
    )

    private lateinit var eventRouter: EventRouter
    private lateinit var requestContext: RequestContext

    @BeforeEach
    internal fun setUp() {
        eventRouter = mockk()
        requestContext = createRequestContext(eventRouter)
    }

    @Test
    fun `test should create a response processor`() {
        ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE))
        )
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.Status::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should submit a response received event`(requestStatus: RequestStatus.Status) {
        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE))
        )

        val processedMessages = randomMessage()
        every { eventRouter.createResponseReceivedEvents(any(), any(), any()) } answers { randomString().toEventID() }

        val messageMatches = mutableListOf(MessageMatches(processedMessages, StatusReceiveBuilder.PASSED))

         responseProcessor.process(
            messageMatches = messageMatches,
            processedMessageIDs = listOf(processedMessages.metadata.id),
            requestContext = requestContext
        )

        val responseMessagesSlot = slot<List<Message>>()

        verify {
            eventRouter.createResponseReceivedEvents(
                messages = capture(responseMessagesSlot),
                eventStatus = com.exactpro.th2.common.event.Event.Status.PASSED,
                parentEventID = requestContext.parentEventID
            )
        }

        expect {
            that(responseMessagesSlot.captured).containsExactly(processedMessages)
        }
    }

    @Test
    fun `test should submit a no response received event`() {
        val noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE))

        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = noResponseBodyFactory
        )

        val processedMessages = 5 of { randomMessage() }

        every { eventRouter.createNoResponseEvent(any(), any(), any()) } answers { randomString().toEventID() }

        assertThrows(NoResponseFoundException::class.java) {
            responseProcessor.process(
                messageMatches = emptyList(),
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

/*    @Test
    fun `test should submit a no matching mapping event`() {
        val processedMessages = 5 of { randomMessage() }
        val receivedMessages = 5 of { randomMessage() }

        val expendedStatus = mutableListOf<MessageStatus>()
        receivedMessages.forEach { expendedStatus.add(MessageStatus(it, StatusReceiveBuilder.PASSED)) }

        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE)),
            expectedMessages = expendedStatus
        )

        every { eventRouter.createNoMappingEvent(any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            responseMessages = receivedMessages.toList(),
            processedMessageIDs = processedMessages.plus(receivedMessages).map { it.metadata.id },
            requestContext = requestContext
        )

        val receivedMessagesSlot = slot<List<Message>>()

        verify {
            eventRouter.createNoMappingEvent(
                expectedMappings = expendedStatus,
                receivedMessages = capture(receivedMessagesSlot),
                parentEventID = requestContext.parentEventID
            )
        }

        expect { // NOTE: Stirkt bug with comparing elements from arrays to elements from a list.
            that(receivedMessagesSlot.captured).containsExactlyInAnyOrder(receivedMessages.toList())
        }
    }*/

    @Test
    fun `test should not send response to client`() {
        val responseProcessor = ResponseProcessor(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(TestMessageType.NEW_ORDER_SINGLE))
        )

        val receivedMessage = TestMessageType.REJECT.toRandomMessage()

        every { eventRouter.createResponseReceivedEvents(any(), any(), any()) } answers { randomString().toEventID() }

        responseProcessor.process(
            messageMatches = listOf(MessageMatches(randomMessage(), StatusReceiveBuilder.PASSED)),
            processedMessageIDs = listOf(receivedMessage.metadata.id),
            requestContext = requestContext
        )
    }

    /* TODO: Add similar scenarios with when there's:
     * 1) No response messages.
     * 2) No matching message mapping.
     */
}