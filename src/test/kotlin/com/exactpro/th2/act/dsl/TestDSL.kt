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

import com.exactpro.th2.act.core.dsl.Context
import com.exactpro.th2.act.core.dsl.ReceiveBuilder
import com.exactpro.th2.act.core.dsl.Responder
import com.exactpro.th2.act.core.dsl.`do`
import com.exactpro.th2.act.core.handlers.decorators.TestSystemResponseReceiver
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.act.toBatch
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.getField
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestDSL {
    private val handler: TestSystemResponseReceiver.StubRequestHandler = spyk { }
    private val subscriptionManager = SubscriptionManager()
    private lateinit var messageRouter: MessageRouter
    private lateinit var eventRouter: EventRouter
    private val parentEventID: EventID = EventID.newBuilder().setId("eventId").build()

    @BeforeEach
    internal fun setUp() {
        val messageBatchRouter: StubMessageRouter<MessageBatch> = StubMessageRouter()
        messageRouter = MessageRouter(messageBatchRouter)
        val eventBatchRouter: StubMessageRouter<EventBatch> = StubMessageRouter()
        eventRouter = EventRouter(eventBatchRouter)
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

    private fun testContext(timeout: Long = 2_000, preFilter: ((Message) -> Boolean)? = null): Context {
        val  requestContext = RequestContext(
            randomString(),
            randomString(),
            messageRouter,
            eventRouter,
            parentEventID,
            Checkpoint.getDefaultInstance(),
            io.grpc.Context.current()
        )
        val responder = Responder()
        if (preFilter != null) responder.addPreFilter(preFilter)
        return Context(requestContext, responder, timeout, handler, subscriptionManager)
    }

    @Test
    fun `send message and wait echo`() {
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.SECOND, 2L).build()
        )

        handler respondsWith { messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) } }

        testContext { msg -> msg.sessionAlias == "sessionAlias" && (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) } `do` {
            assertEquals(messages[1], send(messages[0], "sessionAlias", true))
        }
    }

    @Test
    fun `test should receive response message`() {
        val messages = listOf(
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2).build()
        )

        handler respondsWith { messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) } }

        testContext { msg -> msg.direction == Direction.FIRST && (msg.sessionAlias == "sessionAlias" || msg.sessionAlias == "anotherSessionAlias") } `do` {
            send(messages[0], "sessionAlias")
            assertEquals(
                messages[0],
                receive("NewOrderSingle", "sessionAlias", Direction.FIRST) {
                    passOn("NewOrderSingle") { sequence == 1L }
                    failOn("NewOrderSingle") { sequence == 2L }
                })
        }
    }

    @Test
    fun `test deadline`() {
        val timeout: Long = 1
        val exception = assertThrows(Exception::class.java) {
            testContext(timeout) `do` {
                send(
                    messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L).build(),
                    "sessionAlias"
                )

                receive("NewOrderSingle", "sessionAlias", Direction.FIRST) {
                    passOn("NewOrderSingle") { this.sequence == 1L }
                    failOn("NewOrderSingle") { this.sequence == 2L }
                }
            }
        }
        assertEquals("timeout = $timeout ended before context execution was completed", exception.message)
    }

    @Test
    fun `case one`() {
        val resultBuilder = StubResultBuilder()

        val messages = mutableListOf<Message>()
        var sequence = 1L
        while (sequence < 4L) {
            messages.add(
                messageBuild("QuoteStatusReport", "anotherSessionAlias", Direction.FIRST, sequence)
                    .putFields("quoteId", Value.newBuilder().setSimpleValue("quoteId").build())
                    .putFields("quoteStatus", Value.newBuilder().setSimpleValue("Accepted").build()).build()
            )
            sequence += 1L
        }

        handler respondsWith { messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) } }

        testContext { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "anotherSessionAlias" } `do` {
            val quote: Message = send(
                messageBuild("QuoteStatusReport", "sessionAlias", Direction.FIRST, 1L)
                    .putFields("quoteId", Value.newBuilder().setSimpleValue("quoteId").build()).build(),
                "sessionAlias"
            )

            val quoteStatusReportOne =
                receive("QuoteStatusReport", "anotherSessionAlias", Direction.FIRST) {
                    passOn("QuoteStatusReport") {
                        this.getField("quoteId") == quote.getField("quoteId")
                                && this.getField("quoteStatus") == Value.newBuilder().setSimpleValue("Accepted").build()
                    }
                    failOn("QuoteStatusReport") {
                        this.getField("quoteId") == quote.getField("quoteId")
                                && this.getField("quoteStatus") == Value.newBuilder().setSimpleValue("Rejected").build()
                    }
                }

            if (quoteStatusReportOne != null) {
                resultBuilder.setSingleMessage(quoteStatusReportOne)

                if (quoteStatusReportOne.sequence == 1L) {
                    val quoteStatusReportTwo = receive("QuoteStatusReport", "anotherSessionAlias", Direction.FIRST) {
                        passOn("QuoteStatusReport") {
                            this.getField("quoteId") == quote.getField("quoteId")
                                    && this.getField("quoteStatus") ==
                                    Value.newBuilder().setSimpleValue("Accepted").build()
                        }
                        failOn("QuoteStatusReport") {
                            this.getField("quoteId") == quote.getField("quoteId")
                                    && this.getField("quoteStatus") ==
                                    Value.newBuilder().setSimpleValue("Rejected").build()
                        }
                    }
                    if (quoteStatusReportTwo != null) resultBuilder.setSingleMessage(quoteStatusReportTwo)
                }
            }
        }

        assertEquals(messages[0], resultBuilder.getMessage(0))
        assertEquals(messages[1], resultBuilder.getMessage(1))
    }

    @Test
    fun `case two`() {
        val resultBuilder = StubResultBuilder()

        val messages = mutableListOf<Message>()
        for (it in 3L downTo 1L) {
            messages.add(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, it).build()
            )
        }

        handler respondsWith { messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) } }

        testContext { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" } `do` {
            send(
                messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 4L).build(),
                "sessionAlias"
            )

            resultBuilder.setListMessages(
                repeat {
                    receive("NewOrderSingle", "sessionAlias", Direction.FIRST) {
                        passOn("NewOrderSingle") {
                            direction == Direction.FIRST
                        }
                        failOn("NewOrderSingle") {
                            direction == Direction.SECOND
                        }
                    }!!
                } until { mes ->
                    mes.sequence != 1L
                })
            assertEquals(2, resultBuilder.getMessages().size)
            assertEquals(messages[0], resultBuilder.getMessage(0))
            assertEquals(messages[1], resultBuilder.getMessage(1))
        }
    }


    @Test
    fun `case three`() {
        val resultBuilder = StubResultBuilder()
        var segmentNum = 4

        val expectedMessages = mutableListOf<Message>()
        for (it in 4 downTo 0) {
            expectedMessages.add(updateDQ126(createDQ126(), it.toString()))
        }

        handler respondsWith {
            subscriptionManager.handler(
                randomString(),
                updateDQ126(createDQ126(), segmentNum.toString()).toBatch()
            )
        }

        testContext { msg -> msg.direction == Direction.FIRST && (msg.sessionAlias == "sessionAlias" || msg.sessionAlias == "anotherSessionAlias") } `do` {
            val listMessagesDQ126 = ArrayList<Message>()
            var messageDQ126 = createDQ126()
            do {
                send(messageDQ126, "sessionAlias")

                val responseDQ126 = receive("DQ126", "sessionAlias", Direction.FIRST) {
                    passOn("DQ126") {
                        this.sequence <= 4L
                    }
                    failOn("DQ126") {
                        this.sequence > 4L
                    }
                }
                var segment = "0"
                if (responseDQ126 != null) {
                    listMessagesDQ126.add(responseDQ126)
                    segment = responseDQ126.fieldsMap["segment_number"]!!.simpleValue
                    if (segment.toInt() == 0) break
                    else messageDQ126 = updateDQ126(messageDQ126, segment)

                    segmentNum--
                    handler respondsWith {
                        subscriptionManager.handler(
                            randomString(),
                            updateDQ126(createDQ126(), segmentNum.toString()).toBatch()
                        )
                    }
                }
            } while (segment.toInt() > 0)
            resultBuilder.setListMessages(listMessagesDQ126)
        }

        for (it in 0..3) {
            assertEquals(expectedMessages[it], resultBuilder.getMessage(it))
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
        val resultBuilder = StubResultBuilder()

        val messages = listOf(
            messageBuild("BusinessMessageReject", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("BusinessMessageReject", "sessionAlias", Direction.SECOND, 2L).build()
        )

        handler respondsWith { messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) } }

        testContext { msg -> msg.sessionAlias == "sessionAlias" && (msg.direction == Direction.FIRST || msg.direction == Direction.SECOND) } `do` {
            val echoMessage: Message = send(messages[0], "sessionAlias", true)
            assertEquals(messages[1], echoMessage)

            val message = receive("BusinessMessageReject", "sessionAlias", Direction.FIRST) {
                passOn("BusinessMessageReject") { this.sequence == 1L }
                failOn("BusinessMessageReject") { this.sequence == 2L }
            }
            if (message != null) resultBuilder.setSingleMessage(message)
        }
        assertEquals(messages[0], resultBuilder.getMessage(0))
    }

    @Test
    fun `case five`() {
        val resultBuilder = StubResultBuilder()

        val messages = listOf(
            messageBuild("OrderCancelReplaceRequest", "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild("NewOrderSingle", "sessionAlias", Direction.FIRST, 2L).build()
        )

        handler respondsWith { subscriptionManager.handler(randomString(), messages[0].toBatch()) }

        testContext { msg -> msg.direction == Direction.FIRST && msg.sessionAlias == "sessionAlias" } `do` {
            send(messages[0], "sessionAlias")

            var message = receive("OrderCancelReplaceRequest", "sessionAlias", Direction.FIRST) {
                passOn("OrderCancelReplaceRequest") { this.sequence == 1L }
                failOn("OrderCancelReplaceRequest") { this.sequence == 2L }
            }
            if (message != null) resultBuilder.setSingleMessage(message)

            send(messages[1], "sessionAlias", cleanBuffer = true)

            handler respondsWith { subscriptionManager.handler(randomString(), messages[1].toBatch()) }

            message = receive("NewOrderSingle", "sessionAlias", Direction.FIRST) {
                passOn("NewOrderSingle") { this.sequence == 2L }
                failOn("NewOrderSingle") { this.sequence == 1L }
            }
            if (message != null) resultBuilder.setSingleMessage(message)
        }
        assertEquals(messages[0], resultBuilder.getMessage(0))
        assertEquals(messages[1], resultBuilder.getMessage(1))
    }

    @Test
    fun `test for ReceiveBuilder`() {
        val receiveBuilder = ReceiveBuilder(
            messageBuild(
                "NewOrderSingle",
                "sessionAlias",
                Direction.FIRST,
                1L
            ).build()
        )
        assertEquals(
            true,
            receiveBuilder.passOn("NewOrderSingle") { this.sequence == 1L && this.sessionAlias == "sessionAlias" })
        assertEquals(
            false,
            receiveBuilder.passOn("NewOrderSingle") { this.sequence == 2L && this.sessionAlias == "anotherSessionAlias" })
        assertEquals(
            false,
            receiveBuilder.failOn("NewOrderSingle") { this.sequence == 1L && this.sessionAlias == "sessionAlias" })
        assertEquals(
            true,
            receiveBuilder.failOn("NewOrderSingle") { this.sequence == 2L && this.sessionAlias == "anotherSessionAlias" })
    }
}