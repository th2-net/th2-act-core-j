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

package com.exactpro.th2.act.dsl

import com.exactpro.th2.act.core.dsl.ActionFactory
import com.exactpro.th2.act.core.managers.MessageBatchListener
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.act.toBatch
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.*
import io.grpc.stub.StreamObserver
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class TestDSL {
    private lateinit var messageRouter: MessageRouter
    private lateinit var eventRouter: EventRouter
    private val parentEventID: EventID = EventID.newBuilder().setId("eventId").build()
    private val subscriptionManager: SubscriptionManager = spyk()
    private lateinit var actionFactory: ActionFactory
    private var observer: StreamObserver<Message> = spyk()
    private val eventBatchRouter: StubMessageRouter<EventBatch> = StubMessageRouter()

    @BeforeEach
    internal fun setUp() {
        val messageBatchRouter: StubMessageRouter<MessageBatch> = spyk()
        messageRouter = MessageRouter(messageBatchRouter)
        eventRouter = EventRouter(eventBatchRouter)

        val listeners: Map<Direction, MessageBatchListener> = mapOf(
            Direction.FIRST to getMockListener("First Direction Mock"),
            Direction.SECOND to getMockListener("Second Direction Mock")
        )

        listeners.forEach { (direction, listener) -> subscriptionManager.register(direction, listener) }

        actionFactory = ActionFactory(messageRouter, eventRouter, subscriptionManager)
    }

    private fun getMockListener(name: String? = null): MessageBatchListener {
        return if (name == null) {
            mockk { justRun { handler(any(), any()) } }
        } else {
            mockk(name) { justRun { handler(any(), any()) } }
        }
    }

    private fun messageBuild(
        messageType: String,
        sessionAlias: String,
        direction: Direction,
        sequence: Long
    ): Message.Builder =
        Message.newBuilder().setMetadata(
            MessageMetadata.newBuilder()
                .setMessageType(messageType)
                .setId(
                    MessageID.newBuilder().setConnectionId(
                        ConnectionID.newBuilder().setSessionAlias(sessionAlias).build()
                    ).setDirection(direction)
                )
        ).setParentEventId(parentEventID).apply { this.sequence = sequence }


    @Test
    fun `send message and wait echo`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 2L).build()
        )

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 2000)
                .preFilter { msg -> msg.sessionAlias == "sessionAlias" && (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) }
                .execute {
                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
                    val echoMessage = send(messages[0], "sessionAlias", 1000, true, cleanBuffer = false)
                    emitResult(result.addField("Received a echo message", echoMessage).build())

                    assertEquals(messages[1], echoMessage)
                }
        }
    }

    @Test
    fun `test should receive response message`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L).build()
        )
        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 2000)
                .preFilter { msg -> msg.direction == Direction.FIRST && (msg.sessionAlias == "sessionAlias" || msg.sessionAlias == "anotherSessionAlias") }
                .execute {
                    send(messages[0], "sessionAlias", 1000)

                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
                    val receiveMessage = receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { sequence == 1L }
                        failOn("NewOrderSingle") { sequence == 2L }
                    }

                    emitResult(result.addField("Received a NewOrderSingle message", receiveMessage).build())

                    assertEquals(
                        messages[0], receiveMessage
                    )
                }
        }
    }

    @Test
    fun `case one`() {
        val result = Message.newBuilder()
        val messages = mutableListOf<Message>()
        var sequence = 1L
        while (sequence <= 4L) {
            messages.add(
                messageBuild("QuoteStatusReport", "anotherSessionAlias", Direction.FIRST, sequence)
                    .putFields("quoteId", Value.newBuilder().setSimpleValue("quoteId").build())
                    .putFields("quoteStatus", Value.newBuilder().setSimpleValue("Accepted").build()).build()
            )
            sequence += 1L
        }

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 3000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "anotherSessionAlias" }
                .execute {
                    val quote: Message = send(
                        messageBuild("QuoteStatusReport", "sessionAlias", Direction.FIRST, 1L)
                            .putFields("quoteId", Value.newBuilder().setSimpleValue("quoteId").build()).build(),
                        "sessionAlias", 1000
                    )
                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    val quoteStatusReportOne =
                        receive("QuoteStatusReport", 1000, "anotherSessionAlias", Direction.FIRST) {
                            passOn("QuoteStatusReport") {
                                this.getString("quoteId") == quote.getString("quoteId")
                                        && this.getString("quoteStatus") == "Accepted"
                            }
                            failOn("QuoteStatusReport") {
                                this.getString("quoteId") == quote.getString("quoteId")
                                        && this.getString("quoteStatus") == "Rejected"
                            }
                        }

                    emitResult(result.addField("Received a QuoteStatusReport message", quoteStatusReportOne).build())
                    assertEquals(messages[0], quoteStatusReportOne)

                    if (quoteStatusReportOne.sequence == 1L) {
                        val quoteStatusReportTwo =
                            receive("QuoteStatusReport", 1000, "anotherSessionAlias", Direction.FIRST) {
                                passOn("QuoteStatusReport") {
                                    this.getString("quoteId") == quote.getString("quoteId")
                                            && this.getString("quoteStatus") == "Accepted"
                                }
                                failOn("QuoteStatusReport") {
                                    this.getString("quoteId") == quote.getString("quoteId")
                                            && this.getString("quoteStatus") == "Rejected"
                                }
                            }
                        emitResult(
                            result.addField("Received a QuoteStatusReport message", quoteStatusReportTwo).build()
                        )
                        assertEquals(messages[1], quoteStatusReportTwo)
                    }
                }
        }
    }

    @Test
    fun `case two`() {
        val result = Message.newBuilder()
        val messages = mutableListOf<Message>()
        for (it in 3L downTo 1L) {
            messages.add(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, it).build()
            )
        }

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(
                        messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 4L).build(),
                        "sessionAlias", 1000
                    )

                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    val resultMessages =
                        repeat {
                            receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                                passOn("NewOrderSingle") {
                                    direction == Direction.FIRST
                                }
                                failOn("NewOrderSingle") {
                                    direction == Direction.SECOND
                                }
                            }
                        } until { mes ->
                            mes.sequence != 1L
                        }

                    resultMessages.forEach {
                        emitResult(result.addField("Received a NewOrderSingle message", it).build())
                    }

                    assertEquals(2, resultMessages.size)
                    assertEquals(messages[0], resultMessages[0])
                    assertEquals(messages[1], resultMessages[1])
                }
        }
    }


    @Test
    fun `case three`() {
        val result = Message.newBuilder()
        var segmentNum = 4

        val expectedMessages = mutableListOf<Message>()
        for (it in 4 downTo 0) {
            expectedMessages.add(updateDQ126(createDQ126(), it.toString()))
        }

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10_000)
                .preFilter { msg -> msg.direction == Direction.FIRST && (msg.sessionAlias == "sessionAlias" || msg.sessionAlias == "anotherSessionAlias") }
                .execute {
                    subscriptionManager.handler(
                        randomString(),
                        updateDQ126(createDQ126(), segmentNum.toString()).toBatch()
                    )

                    val listMessagesDQ126 = ArrayList<Message>()
                    var messageDQ126 = createDQ126()
                    do {
                        send(messageDQ126, "sessionAlias", 1000, cleanBuffer = false)

                        val responseDQ126 = receive("DQ126", 1000, "sessionAlias", Direction.FIRST) {
                            passOn("DQ126") {
                                this.sequence <= 4L
                            }
                            failOn("DQ126") {
                                this.sequence > 4L
                            }
                        }
                        listMessagesDQ126.add(responseDQ126)
                        val segment: String = responseDQ126.fieldsMap["segment_number"]!!.simpleValue
                        if (segment.toInt() == 0) break
                        else messageDQ126 = updateDQ126(messageDQ126, segment)

                        segmentNum--
                        subscriptionManager.handler(
                            randomString(),
                            updateDQ126(createDQ126(), segmentNum.toString()).toBatch()
                        )

                    } while (segment.toInt() > 0)
                    listMessagesDQ126.forEach {
                        emitResult(result.addField("Received a DQ126 message", it).build())
                    }
                    for (it in 0..3) {
                        assertEquals(expectedMessages[it], listMessagesDQ126[it])
                    }
                }
        }
    }

    private fun createDQ126(): Message = Message.newBuilder()
        .setMetadata(
            MessageMetadata.newBuilder()
                .setMessageType("DQ126")
                .setId(
                    MessageID.newBuilder()
                        .setConnectionId(
                            ConnectionID.newBuilder()
                                .setSessionAlias("sessionAlias")
                                .build()
                        ).build()
                ).build()
        ).build()

    private fun updateDQ126(dq126: Message, segment: String): Message = dq126.toBuilder()
        .putFields("segment_number", Value.newBuilder().setSimpleValue(segment).build()).build()


    @Test
    fun `case four`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("BusinessMessageReject", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("BusinessMessageReject", "sessionAlias", Direction.SECOND, 2L).build()
        )

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.sessionAlias == "sessionAlias" && (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) }
                .execute {
                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    val echoMessage: Message = send(messages[0], "sessionAlias", 1000, true, cleanBuffer = false)
                    assertEquals(messages[1], echoMessage)

                    val message = receive("BusinessMessageReject", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("BusinessMessageReject") { this.sequence == 1L }
                        failOn("BusinessMessageReject") { this.sequence == 2L }
                    }

                    emitResult(result.addField("Received a BusinessMessageReject message", message).build())
                    assertEquals(messages[0], message)
                }
        }
    }

    @Test
    fun `case five`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("OrderCancelReplaceRequest", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L).build()
        )

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(messages[0], "sessionAlias", 1000)

                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    var message = receive("OrderCancelReplaceRequest", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("OrderCancelReplaceRequest") { this.sequence == 1L }
                        failOn("OrderCancelReplaceRequest") { this.sequence == 2L }
                    }
                    emitResult(message)

                    assertEquals(messages[0], message)
                    send(messages[1], "sessionAlias", 1000, cleanBuffer = false)

                    message = receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { this.sequence == 2L }
                        failOn("NewOrderSingle") { this.sequence == 1L }
                    }
                    emitResult(result.addField("Received a NewOrderSingle message", message).build())
                    assertEquals(messages[1], message)
                }
        }
    }

    @Test
    fun `receiving messages from two directions`() {
        val messages = mutableListOf<Message>()
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L).build() )
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L).build() )
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 3L).build() )
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 4L).build() )

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(
                        messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 4L).build(),
                        "sessionAlias", 1000
                    )

                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    var message = receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") {
                            this.sequence == 1L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 5L
                        }
                    }
                    assertEquals(messages[0], message)

                    message = receive("NewOrderSingle", 1000, "sessionAlias", Direction.SECOND) {
                        passOn("NewOrderSingle") {
                            this.sequence == 4L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 5L
                        }
                    }
                    assertEquals(messages[3], message)
                }
        }
    }

    @Test
    fun `message not found`() {
        val messages = mutableListOf<Message>()
        for (it in 3L downTo 1L) {
            messages.add(
                messageBuild("OrderCancelReplaceRequest", "sessionAlias", Direction.FIRST, it).build()
            )
        }

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(
                        messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 4L).build(),
                        "sessionAlias", 1000
                    )

                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") {
                            this.sequence == 1L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 2L
                        }
                    }
                }
        }

        expect {
            that(eventBatchRouter.sent.eventsList).any {
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { type }.isEqualTo("Error")
                get { name }.contains("An Error has occurred")
                get { name }.contains("Unexpected behavior. The message to receive was not found.")
            }
        }
    }

    @Test
    fun `receiving a message for failOn`() {
        val messages = mutableListOf<Message>()
        for (it in 5L downTo 1L) {
            messages.add(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, it).build()
            )
        }

        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(messages[0], "sessionAlias", 1000)

                    messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }

                    receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") {
                            this.sequence == 1L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 4L
                        }
                    }
                }
        }

        expect {
            that(eventBatchRouter.sent.eventsList).any {
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { type }.isEqualTo("Error")
                get { name }.contains("An Error has occurred")
                get { name }.contains("Found a message for failOn.")
            }
        }
    }

    @Test
    fun `the deadline has ended`() {
        val timeout = 3L
        actionFactory.apply {
            createAction(observer, randomString(), randomString(), parentEventID, timeout)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "anotherSessionAlias" }
                .execute {
                    send(
                        messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L).build(),
                        "sessionAlias", 1000
                    )

                    receive("NewOrderSingle", 1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { this.sequence == 1L }
                        failOn("NewOrderSingle") { this.sequence == 2L }
                    }
                }
        }
    }
}