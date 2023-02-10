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

import com.exactpro.th2.act.DUMMY_BOOK_NAME
import com.exactpro.th2.act.DUMMY_SCOPE_NAME
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import com.exactpro.th2.act.core.messages.message
import com.exactpro.th2.check1.grpc.Check1Service
import com.exactpro.th2.common.schema.message.DeliveryMetadata

class TestDSL {
    lateinit var messageRouter: MessageRouter
    lateinit var eventRouter: EventRouter
    val parentEventID: EventID = EventID.newBuilder()
        .setId("eventId")
        .setBookName(DUMMY_BOOK_NAME)
        .setScope(DUMMY_SCOPE_NAME)
        .build()
    val subscriptionManager: SubscriptionManager = spyk()
    lateinit var actionFactory: ActionFactory
    var observer: StreamObserver<Message> = spyk()
    val eventBatchRouter: StubMessageRouter<EventBatch> = StubMessageRouter()
    val check1Service: Check1Service = spyk()

    val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val rpcName = randomString()

    @BeforeEach
    internal fun setUp() {
        val messageBatchRouter: StubMessageRouter<MessageBatch> = spyk()
        messageRouter = MessageRouter(messageBatchRouter)
        eventRouter = EventRouter(eventBatchRouter, DUMMY_BOOK_NAME, DUMMY_SCOPE_NAME)

        val listeners: Map<Direction, MessageBatchListener> = mapOf(
            Direction.FIRST to getMockListener("First Direction Mock"),
            Direction.SECOND to getMockListener("Second Direction Mock")
        )

        listeners.forEach { (direction, listener) -> subscriptionManager.register(direction, listener) }

        actionFactory = ActionFactory(messageRouter, eventRouter, subscriptionManager, check1Service)
    }

    fun getMockListener(name: String? = null): MessageBatchListener {
        return if (name == null) {
            mockk { justRun { handle(any(), any()) } }
        } else {
            mockk(name) { justRun { handle(any(), any()) } }
        }
    }

    fun messageBuild(
        messageType: String,
        sessionAlias: String,
        direction: Direction,
        sequence: Long,
        field: Map<String, String> = mutableMapOf()
    ): Message = message(messageType, ConnectionID.newBuilder().setSessionAlias(sessionAlias).build()) {
            parentEventId = parentEventID
            metadata {
                this.direction = direction
                this.sequence = sequence
            }
            body {
                field.forEach { (key, value) ->
                    key to value
                }
            }
        }


    @Test
    fun `send message and wait echo`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 2L)
        )

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 2000)
                .preFilter { msg -> msg.sessionAlias == "sessionAlias"
                        && (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) }
                .execute {
                    messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }

                    val echoMessage = send(
                        messages[0], "sessionAlias", 1000, waitEcho = true, cleanBuffer = false
                    )

                    emitResult(result.addField("Received a echo message", echoMessage).build())

                    assertEquals(messages[1], echoMessage)
                }
        }
        checkingSentEvent("NewOrderSingle")
    }

    @Test
    fun `test should receive response message`() {
        val result = Message.newBuilder()
        val messages = mutableListOf<Message>()
        for (sequence in 1L .. 5L) {
            messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, sequence))
        }

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 2000)
                .preFilter { msg -> msg.direction == Direction.FIRST
                        && (msg.sessionAlias == "sessionAlias" || msg.sessionAlias == "anotherSessionAlias") }
                .execute {
                    send(messages[0], "sessionAlias", 1000)

                    messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                    val receiveMessage = receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { sequence == 2L }
                        failOn("NewOrderSingle") { sequence == 3L }
                    }

                    emitResult(result.addField("Received a NewOrderSingle message", receiveMessage).build())

                    assertEquals(messages[1], receiveMessage)
                }
        }
        checkingSentEvent("NewOrderSingle")
    }

    @Test
    fun `case one`() {
        val result = Message.newBuilder()
        val messages = mutableListOf<Message>()
        var sequence = 1L
        while (sequence <= 4L) {
            messages.add(
                messageBuild("QuoteStatusReport",
                    if (sequence == 1L) "sessionAlias" else "anotherSessionAlias",
                    Direction.FIRST, sequence, mapOf("quoteId" to "quoteId", "quoteStatus" to "Accepted"))
            )
            sequence++
        }

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 3000)
                .execute {
                    val quote: Message = send(messages[0], timeout = 1000)

                    executorService.schedule(
                        fun() {
                            messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                        },
                        500,
                        TimeUnit.MILLISECONDS
                    )

                    val quoteStatusReportOne =
                        receive(1000, "sessionAlias", Direction.FIRST) {
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
                            receive(1000, "anotherSessionAlias", Direction.FIRST) {
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
        checkingSentEvent("QuoteStatusReport")
    }

    @Test
    fun `case two`() {
        val result = Message.newBuilder()
        val messages = mutableListOf<Message>()
        for (sequence in 1L..5L) {
            messages.add(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, sequence)
            )
        }

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(messages[3], "sessionAlias", 1000)

                    executorService.scheduleAtFixedRate(
                        fun() {
                            messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                        },
                        0,
                        1500,
                        TimeUnit.MILLISECONDS
                    )

                    val resultMessages =
                        repeat {
                            receive(1000, "sessionAlias", Direction.FIRST) {
                                passOn("NewOrderSingle") {
                                    direction == Direction.FIRST
                                }
                                failOn("NewOrderSingle") {
                                    direction == Direction.SECOND
                                }
                            }
                        } until { mes ->
                            mes.sequence != 2L
                        }

                    resultMessages.forEach {
                        emitResult(result.addField("Received a NewOrderSingle message", it).build())
                    }

                    assertEquals(2, resultMessages.size)
                    assertEquals(messages[0], resultMessages[0])
                    assertEquals(messages[1], resultMessages[1])
                }
        }
        checkingSentEvent("NewOrderSingle")
    }


    @Test
    fun `case three`() {
        val result = Message.newBuilder()

        val expectedMessages = mutableListOf<Message>()
        for (segment in 1..5) {
            expectedMessages.add(updateDQ126(createDQ126(), segment.toString()))
        }

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10_000)
                .preFilter { msg -> msg.direction == Direction.FIRST && (msg.sessionAlias == "sessionAlias" || msg.sessionAlias == "anotherSessionAlias") }
                .execute {
                    expectedMessages.forEach {
                        subscriptionManager.handle(
                            DeliveryMetadata(randomString()), it.toBatch()
                        )
                    }

                    val listMessagesDQ126 = ArrayList<Message>()
                    var messageDQ126 = createDQ126()
                    var i = 0
                    do {
                        send(messageDQ126, "sessionAlias", 1000, cleanBuffer = false)

                        executorService.schedule(
                            fun() {
                                subscriptionManager.handle(DeliveryMetadata(randomString()), expectedMessages[i].toBatch())
                            },
                            1500,
                            TimeUnit.MILLISECONDS
                        )
                        i++

                        val responseDQ126 = receive(1000, "sessionAlias", Direction.FIRST) {
                            passOn("DQ126") {
                                this.sequence <= 5L
                            }
                            failOn("DQ126") {
                                this.sequence > 5L
                            }
                        }
                        listMessagesDQ126.add(responseDQ126)
                        val segment: String = responseDQ126.fieldsMap["segment_number"]!!.simpleValue
                        if (segment.toInt() == 0) break
                        else messageDQ126 = updateDQ126(messageDQ126, segment)

                    } while (segment.toInt() > 1)
                    listMessagesDQ126.forEach {
                        emitResult(result.addField("Received a DQ126 message", it).build())
                    }
                    for (it in 1..4) {
                        assertEquals(expectedMessages[it], listMessagesDQ126[it])
                    }
                }
        }
        checkingSentEvent("DQ126")
    }

    fun createDQ126(): Message =
        message("DQ126", ConnectionID.newBuilder().setSessionAlias("sessionAlias").build()) {
        }

    fun updateDQ126(dq126: Message, segment: String): Message = dq126.toBuilder()
        .putFields("segment_number", Value.newBuilder().setSimpleValue(segment).build())
        .apply { this.sequence = segment.toLong() }.build()


    @Test
    fun `case four`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("BusinessMessageReject", "sessionAlias", Direction.SECOND, 1L),
            messageBuild("BusinessMessageReject", "sessionAlias", Direction.FIRST, 2L)
        )

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.sessionAlias == "sessionAlias" && (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) }
                .execute {
                    executorService.schedule(
                        fun() {
                            messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                        },
                        500,
                        TimeUnit.MILLISECONDS
                    )

                    val echoMessage: Message = send(messages[1], "sessionAlias", 1000, true, cleanBuffer = false)
                    assertEquals(messages[0], echoMessage)

                    val message = receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("BusinessMessageReject") { this.sequence == 2L }
                        failOn("BusinessMessageReject") { this.sequence == 3L }
                    }
                    emitResult(result.addField("Received a BusinessMessageReject message", message).build())
                    assertEquals(messages[1], message)
                }
        }
        checkingSentEvent("BusinessMessageReject")
    }

    @Test
    fun `case five`() {
        val result = Message.newBuilder()
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L),
            messageBuild("OrderCancelReplaceRequest", "sessionAlias", Direction.FIRST, 2L)
        )
        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    executorService.schedule(
                        fun() {
                            messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                        },
                        500,
                        TimeUnit.MILLISECONDS
                    )
                    send(messages[0], "sessionAlias", 1000, cleanBuffer = false)

                    var message = receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { this.sequence == 1L }
                        failOn("NewOrderSingle") { this.sequence == 2L }
                    }
                    emitResult(result.addField("Received a NewOrderSingle message", message).build())
                    assertEquals(messages[0], message)

                    send(messages[1], "sessionAlias", 1000, cleanBuffer = false)

                    message = receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("OrderCancelReplaceRequest") { this.sequence == 2L }
                        failOn("OrderCancelReplaceRequest") { this.sequence == 1L }
                    }
                    emitResult(message)
                }
        }
        checkingSentEvent("OrderCancelReplaceRequest")
    }

    @Test
    fun `testing reject`(){
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 2L),
            messageBuild("Reject", "sessionAlias", Direction.FIRST, 2L),
        )

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.sessionAlias == "sessionAlias" && (msg.direction == Direction.FIRST
                        || msg.direction == Direction.SECOND) }
                .execute {
                    messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }

                    val echoMessage: Message = send(messages[0], "sessionAlias", 1000, true, cleanBuffer = false)
                    assertEquals(messages[1], echoMessage)

                    receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { this.sequence == 3L }
                        failOn("NewOrderSingle") { this.sequence == 4L }
                        failOn("Reject") { this.sequence == echoMessage.sequence }
                    }
                }
        }
        expect {
            that(eventBatchRouter.sent.eventsList).any {
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { type }.isEqualTo("Received Message FailOn")
                get { name }.contains("Received a Reject message for failOn")
                get { name }.contains("Found a message for failOn.")
            }
        }
    }

    @Test
    fun `receiving messages from two directions`() {
        val messages = mutableListOf<Message>()
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L))
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L))
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 3L))
        messages.add(messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 4L))


        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(
                        message("NewOrderSingle", ConnectionID.newBuilder().setSessionAlias("sessionAlias").build()) {
                            parentEventId = parentEventID
                        },
                        timeout = 1000
                    )

                    executorService.schedule(
                        fun() {
                            messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                        },
                        500,
                        TimeUnit.MILLISECONDS
                    )

                    var message = receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") {
                            this.sequence == 1L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 2L
                        }
                    }
                    assertEquals(messages[0], message)

                    message = receive(1000, "sessionAlias", Direction.SECOND) {
                        passOn("NewOrderSingle") {
                            this.sequence == 4L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 6L
                        }
                    }
                    assertEquals(messages[3], message)
                }
        }
        checkingSentEvent("NewOrderSingle")
    }

    @Test
    fun `message not found`() {
        val messages = mutableListOf<Message>()
        for (sequence in 1L..4L) {
            messages.add(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, sequence)
            )
        }

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(messages[3], "sessionAlias", 1000)

                    executorService.schedule(
                        fun() {
                            messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }
                        },
                        1500,
                        TimeUnit.MILLISECONDS
                    )

                    receive(1000, "sessionAlias", Direction.FIRST) {
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
                get { name }.contains("No Response Received")
                get { name }.contains("The expected response was not received")
            }
        }
    }

    @Test
    fun `receiving a message for failOn`() {
        val messages = mutableListOf<Message>()
        for (sequence in 1L..5L) {
            messages.add(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, sequence)
            )
        }

        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, 10000)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" }
                .execute {
                    send(messages[0], "sessionAlias", 1000)

                    messages.forEach { subscriptionManager.handle(DeliveryMetadata(randomString()), it.toBatch()) }

                    receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") {
                            this.sequence == 4L
                        }
                        failOn("NewOrderSingle") {
                            this.sequence == 1L
                        }
                    }
                }
        }

        expect {
            that(eventBatchRouter.sent.eventsList).any {
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.FAILED)
                get { type }.isEqualTo("Received Message FailOn")
                get { name }.contains("Received a NewOrderSingle message for failOn")
                get { name }.contains("Found a message for failOn.")
            }
        }
    }

    @Test
    fun `the deadline has ended`() {
        val timeout = 3L
        actionFactory.apply {
            createAction(observer, rpcName, randomString(), parentEventID, timeout)
                .preFilter { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "anotherSessionAlias" }
                .execute {
                    send(
                        messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L),
                        "sessionAlias", 1000
                    )

                    receive(1000, "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") { this.sequence == 1L }
                        failOn("NewOrderSingle") { this.sequence == 2L }
                    }
                }
        }
    }

    fun checkingSentEvent(messageType: String){
        expect {
            that(eventBatchRouter.sent.eventsList).any {
                get { parentId }.isEqualTo(parentEventID)
                get { status }.isEqualTo(EventStatus.SUCCESS)
                get { name }.isEqualTo("Received a $messageType message")
                get { type }.contains("Received Message")
            }
        }
    }
}