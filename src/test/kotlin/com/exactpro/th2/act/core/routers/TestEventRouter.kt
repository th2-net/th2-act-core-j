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

package com.exactpro.th2.act.core.routers

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.messages.MessageMatches
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.response.IBodyDataFactory
import com.exactpro.th2.act.core.response.NoResponseBodyFactory
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.common.event.Event.Status
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.*
import java.io.IOException
import java.util.function.Consumer
import java.util.stream.Stream

internal class TestEventRouter {

    /* Test List for EventRouter:
     * 1) Should submit an event to the Event Batch Router when calling storeEvent. V
     * 2) Should submit all events to the Event Batch Router when calling storeEvents. V
     * 3) Should throw an IOException if an error occurs while submitting an event. V
     * 4) Should create an event, submit it and return it's event ID when calling createParentEvent. V
     * 5) Should create an event, submit it and return it's event ID when calling createSendMessageEvent. V
     * 6) Should create an event, submit it and return it's event ID when calling createErrorEvent. V
     * 7) Should create events and submit them when calling createResponseReceivedEvents. V
     * 8) Should create an event, submit it and returns it's event ID when calling createNoResponseEvent. V
     * 9) Should create an event, submit it and returns it's event ID when calling createNoMappingEvent. V
     * 10) Should throw a EventSubmissionException if an error occurs while submitting an event. V
     */

    lateinit var eventBatchRouter: StubMessageRouter<EventBatch>
    lateinit var eventRouter: EventRouter

    @BeforeEach
    internal fun setUp() {
        eventBatchRouter = StubMessageRouter()
        eventRouter = EventRouter(eventBatchRouter)
    }

    @Test
    fun `test should submit an event to the event batch router`() {
        val event = randomEvent()
        val expectedProtoEvent = event.toProto(null)
        eventRouter.storeEvent(event)

        expect {
            that(eventBatchRouter.sent.eventsList).containsExactly(expectedProtoEvent)
        }
    }

    @Test
    fun `test should submit multiple events to the event batch router`() {
        val events = 5 of { randomEvent() }
        val expectedProtoEvents = events.map { it.toProto(null) }.toList() // Stirkt Bug: Must be list.
        eventRouter.storeEvents(events.toList())

        expect {
            that(eventBatchRouter.sent.eventsList).containsExactlyInAnyOrder(expectedProtoEvents)
        }
    }

    @Test
    fun `test should throw exception if an error occurs while submitting an event`() {
        eventBatchRouter.throws { IOException() }

        assertThrows<Exception> { eventRouter.storeEvent(randomEvent()) }
    }

    @Test
    fun `test should throw exception if an error occurs while submitting multiple events`() {
        eventBatchRouter.throws { IOException() }

        assertThrows<Exception> { eventRouter.storeEvents(5.of { randomEvent() }.toList()) }
    }

    @ParameterizedTest
    @EnumSource(Status::class)
    fun `test should create parent event`(eventStatus: Status) {
        val request = Request(randomMessage(), randomString())
        val rpcName = randomString()
        val eventID = eventRouter.createParentEvent(request, rpcName, eventStatus)

        val expectedStatus = when (eventStatus) {
            Status.PASSED -> EventStatus.SUCCESS
            Status.FAILED -> EventStatus.FAILED
        }

        expect {
            that(eventBatchRouter.sent.eventsList).any {

                get { id }.isEqualTo(eventID)
                get { parentId }.isEqualTo(request.requestMessage.parentEventId)
                get { status }.isEqualTo(expectedStatus)
                get { type }.isEqualTo(rpcName)

                get { name }.contains(rpcName)
                get { name }.contains(request.requestDescription)
                get { name }.contains(request.requestMessage.metadata.id.connectionId.sessionAlias)
            }
        }
    }

    @Test
    fun `test should create send message event`() {
        val message = randomMessage()
        val parentEventID = randomString().toEventID()
        val eventID = eventRouter.createSendMessageEvent(message, parentEventID)

        expect {
            that(eventBatchRouter.sent.eventsCount).isEqualTo(1)
            that(eventBatchRouter.sent.eventsList.first()) {

                get { id }.isEqualTo(eventID)
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.SUCCESS)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Status::class)
    fun `test should create a message received event`(eventStatus: Status) {
        val messages = 5 of { randomMessage() }
        val parentEventID = randomString().toEventID()

        val expectedStatus = when (eventStatus) {
            Status.PASSED -> EventStatus.SUCCESS
            Status.FAILED -> EventStatus.FAILED
        }

        eventRouter.createResponseReceivedEvents(
            messages = messages.toList(),
            eventStatus = eventStatus,
            parentEventID = parentEventID
        )

        expect {
            that(eventBatchRouter.sent.eventsList.size).isEqualTo(messages.size)
            that(eventBatchRouter.sent.eventsList.flatMap { it.attachedMessageIdsList }).containsExactlyInAnyOrder(
                messages.map { it.metadata.id }
            )
            that(eventBatchRouter.sent.eventsList).all {

                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(expectedStatus)
            }
        }
    }

    @Test
    fun `test should create a no response event`() {
        val messageIDs = 5.of { randomMessage() }.map { it.metadata.id }
        val parentEventID = randomString().toEventID()

        val eventID = eventRouter.createNoResponseEvent(
            noResponseBodyFactory = NoResponseBodyFactory(listOf(randomMessageType())),
            processedMessageIDs = messageIDs,
            parentEventID = parentEventID
        )

        expect {
            that(eventBatchRouter.sent.eventsList.size).isEqualTo(1)
            that(eventBatchRouter.sent.eventsList.first()) {

                get { id }.isEqualTo(eventID)
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { attachedMessageIdsList }.containsExactlyInAnyOrder(messageIDs)
            }
        }
    }

    @Test
    fun `test should create a no mapping event`() {
        val messages = 5.randomMessageTypes(TestMessageType.REJECT).map { it.toRandomMessage() }
        val parentEventID = randomString().toEventID()

        val messagesMatches = mutableListOf<MessageMatches>()
        messages.forEach { messagesMatches.add(MessageMatches(it, Status.PASSED)) }

        val eventID = eventRouter.createNoMappingEvent(
            messagesMatches = messagesMatches,
            parentEventID = parentEventID
        )

        expect {
            that(eventBatchRouter.sent.eventsList.size).isEqualTo(1)
            that(eventBatchRouter.sent.eventsList.first()) {

                get { id }.isEqualTo(eventID)
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { attachedMessageIdsList }.containsExactlyInAnyOrder(messages.map { it.metadata.id })
            }
        }
    }

    @Test
    fun `test should create an error event`() {
        val cause = randomString()
        val parentEventID = randomString().toEventID()
        val eventID = eventRouter.createErrorEvent(cause, parentEventID)

        expect {
            that(eventBatchRouter.sent.eventsList).any {

                get { id }.isEqualTo(eventID)
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { name }.contains(cause)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provideEventCreationCallers")
    fun `test should throw an exception when creating an event`(caller: Consumer<EventRouter>) {
        eventBatchRouter.throws { IOException() }

        expectThrows<EventSubmissionException> { caller.accept(eventRouter) }
    }

    companion object {

        @JvmStatic
        @SuppressWarnings("unused")
        fun provideEventCreationCallers(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Consumer { eventRouter: EventRouter ->
                    eventRouter.createParentEvent(
                        request = randomRequest(),
                        rpcName = randomString(),
                        status = randomEventStatus()
                    )
                }),
                Arguments.of(Consumer { eventRouter: EventRouter ->
                    eventRouter.createErrorEvent(
                        cause = randomString(),
                        parentEventID = randomString().toEventID()
                    )
                }),
                Arguments.of(Consumer { eventRouter: EventRouter ->
                    eventRouter.createSendMessageEvent(
                        message = randomMessage(),
                        parentEventID = randomString().toEventID()
                    )
                }),
                Arguments.of(Consumer { eventRouter: EventRouter ->
                    eventRouter.createNoResponseEvent(
                        noResponseBodyFactory = IBodyDataFactory { emptyList() },
                        processedMessageIDs = 5.of { randomMessage() }.map { it.metadata.id },
                        parentEventID = randomString().toEventID()
                    )
                }),
                Arguments.of(Consumer { eventRouter: EventRouter ->
                    eventRouter.createNoMappingEvent(
                        messagesMatches = 5.of { MessageMatches(randomMessage(), Status.PASSED) }.toList(),
                        parentEventID = randomString().toEventID()
                    )
                }),
                Arguments.of(Consumer { eventRouter: EventRouter ->
                    eventRouter.createResponseReceivedEvents(
                        messages = 5.of { randomMessage() }.toList(),
                        eventStatus = Status.values().random(),
                        parentEventID = randomString().toEventID()
                    )
                })
            )
        }
    }
}
