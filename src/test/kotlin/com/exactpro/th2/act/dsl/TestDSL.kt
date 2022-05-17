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

import com.exactpro.th2.act.TestMessageType
import com.exactpro.th2.act.core.dsl.Context
import com.exactpro.th2.act.core.dsl.ReceiveBuilder
import com.exactpro.th2.act.core.dsl.`do`
import com.exactpro.th2.act.core.dsl.subscriptionManager
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestDSL {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var messageRouter: MessageRouter
    private lateinit var eventRouter: EventRouter
    private lateinit var handler: TestSystemResponseReceiver.StubRequestHandler
    private val parentEventID: EventID = EventID.newBuilder().setId("eventId").build()
    private lateinit var requestContext: RequestContext

    @BeforeEach
    internal fun setUp() {
        handler = spyk { }
        val messageBatchRouter: StubMessageRouter<MessageBatch> = StubMessageRouter()
        messageRouter = MessageRouter(messageBatchRouter)
        val eventBatchRouter: StubMessageRouter<EventBatch> = StubMessageRouter()
        eventRouter = EventRouter(eventBatchRouter)
        requestContext = RequestContext(
            randomString(),
            randomString(),
            messageRouter,
            eventRouter,
            parentEventID,
            Checkpoint.getDefaultInstance(),
            io.grpc.Context.current()
        )
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
        val messages = listOf(
            messageBuild(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.SECOND, 2L).build()
        )

        subscriptionManager = subscriptionManager(listOf("sessionAlias"), listOf(Direction.FIRST, Direction.SECOND))
        handler respondsWith {
            messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        Context(handler, subscriptionManager, requestContext) `do` {
            assertEquals(messages[1], send(messages[0], "sessionAlias", true, 2_000))
        }
    }

    @Test
    fun `test should receive response message`() {
        val messages = listOf(
            messageBuild(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 2).build()
        )

        subscriptionManager =
            subscriptionManager(listOf("sessionAlias", "anotherSessionAlias"), listOf(Direction.FIRST))

        handler respondsWith {
            messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        Context(handler, subscriptionManager, requestContext) `do` {
            send(messages[0], "sessionAlias")
            assertEquals(
                messages[0],
                receive(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.NEW_ORDER_SINGLE.typeName) {
                        sequence == 1L
                    }
                    failOn(TestMessageType.NEW_ORDER_SINGLE.typeName) {
                        sequence == 2L
                    }
                })
        }
    }

    @Test
    fun `case one`() {
        val resultBuilder = StubResultBuilder()

        val messages = mutableListOf<Message>()
        var sequence = 1L
        while (sequence < 4L) {
            messages.add(
                messageBuild(
                    TestMessageType.QUOTE_STATUS_REPORT.typeName,
                    "anotherSessionAlias",
                    Direction.FIRST,
                    sequence
                )
                    .putFields("quoteId", Value.newBuilder().setSimpleValue("quoteId").build())
                    .putFields("quoteStatus", Value.newBuilder().setSimpleValue("Accepted").build()).build()
            )
            sequence += 1L
        }

        subscriptionManager = subscriptionManager(listOf("anotherSessionAlias"), listOf(Direction.FIRST))

        handler respondsWith {
            messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        Context(handler, subscriptionManager, requestContext) `do` {
            val quote: Message = send(
                messageBuild(
                    TestMessageType.QUOTE_STATUS_REPORT.typeName,
                    "sessionAlias",
                    Direction.FIRST,
                    1L
                ).putFields("quoteId", Value.newBuilder().setSimpleValue("quoteId").build()).build(),
                "sessionAlias"
            )

            val quoteStatusReportOne =
                receive(TestMessageType.QUOTE_STATUS_REPORT.typeName, "anotherSessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.QUOTE_STATUS_REPORT.typeName) {
                        this.getField("quoteId") == quote.getField("quoteId")
                                && this.getField("quoteStatus") == Value.newBuilder().setSimpleValue("Accepted").build()
                    }
                    failOn(TestMessageType.QUOTE_STATUS_REPORT.typeName) {
                        this.getField("quoteId") == quote.getField("quoteId")
                                && this.getField("quoteStatus") == Value.newBuilder().setSimpleValue("Rejected").build()
                    }
                }

            resultBuilder.setSingleMessage(quoteStatusReportOne)

            if (quoteStatusReportOne.sequence == 1L) {
                resultBuilder.setSingleMessage(
                    receive(
                        TestMessageType.QUOTE_STATUS_REPORT.typeName,
                        "anotherSessionAlias",
                        Direction.FIRST,
                        2_000
                    ) {
                        passOn(TestMessageType.QUOTE_STATUS_REPORT.typeName) {
                            this.getField("quoteId") == quote.getField("quoteId")
                                    && this.getField("quoteStatus") ==
                                    Value.newBuilder().setSimpleValue("Accepted").build()
                        }
                        failOn(TestMessageType.QUOTE_STATUS_REPORT.typeName) {
                            this.getField("quoteId") == quote.getField("quoteId")
                                    && this.getField("quoteStatus") ==
                                    Value.newBuilder().setSimpleValue("Rejected").build()
                        }
                    })
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
                messageBuild(
                    TestMessageType.NEW_ORDER_SINGLE.typeName,
                    "sessionAlias",
                    Direction.FIRST,
                    it
                ).build()
            )
        }

        subscriptionManager = subscriptionManager(listOf("sessionAlias"), listOf(Direction.FIRST))

        handler respondsWith {
            messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        Context(handler, subscriptionManager, requestContext) `do` {
            send(
                messageBuild(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 4L).build(),
                "sessionAlias"
            )

            resultBuilder.setListMessages(repeatUntil { mes ->
                mes.sequence != 1L
            } `do` {
                receive(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.NEW_ORDER_SINGLE.typeName) {
                        direction == Direction.FIRST
                    }
                    failOn(TestMessageType.NEW_ORDER_SINGLE.typeName) {
                        direction == Direction.SECOND
                    }
                }
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

        subscriptionManager =
            subscriptionManager(listOf("sessionAlias", "anotherSessionAlias"), listOf(Direction.FIRST))

        handler respondsWith {
            subscriptionManager.handler(randomString(), updateDQ126(createDQ126(), segmentNum.toString()).toBatch())
        }

        Context(handler, subscriptionManager, requestContext) `do` {
            val listMessagesDQ126 = ArrayList<Message>()
            var messageDQ126 = createDQ126()
            do {
                send(messageDQ126, "sessionAlias")

                val responseDQ126 = receive(TestMessageType.DQ126.typeName, "sessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.DQ126.typeName) {
                        this.sequence <= 4L
                    }
                    failOn(TestMessageType.DQ126.typeName) {
                        this.sequence > 4L
                    }
                }
                listMessagesDQ126.add(responseDQ126)
                val segment: String = responseDQ126.fieldsMap["segment_number"]!!.simpleValue
                if (segment.toInt() == 0) break
                else messageDQ126 = updateDQ126(messageDQ126, segment)

                segmentNum--
                handler respondsWith {
                    subscriptionManager.handler(
                        randomString(),
                        updateDQ126(createDQ126(), segmentNum.toString()).toBatch()
                    )
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
            messageBuild(TestMessageType.BUSINESS_MESSAGE_REJECT.typeName, "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild(TestMessageType.BUSINESS_MESSAGE_REJECT.typeName, "sessionAlias", Direction.SECOND, 2L).build()
        )

        subscriptionManager = subscriptionManager(listOf("sessionAlias"), listOf(Direction.FIRST, Direction.SECOND))

        handler respondsWith { messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) } }

        Context(handler, subscriptionManager, requestContext) `do` {
            val echoMessage: Message = send(messages[0], "sessionAlias", true)
            assertEquals(messages[1], echoMessage)

            resultBuilder.setSingleMessage(
                receive(TestMessageType.BUSINESS_MESSAGE_REJECT.typeName, "sessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.BUSINESS_MESSAGE_REJECT.typeName) { this.sequence == 1L }
                    failOn(TestMessageType.BUSINESS_MESSAGE_REJECT.typeName) { this.sequence == 2L }
                }
            )
        }
        assertEquals(messages[0], resultBuilder.getMessage(0))
    }

    @Test
    fun `case five`() {
        val resultBuilder = StubResultBuilder()

        val messages = listOf(
            messageBuild(TestMessageType.ORDER_CANCEL_REJECT.typeName, "sessionAlias", Direction.FIRST, 1L).build(),
            messageBuild(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 2L).build()
        )

        subscriptionManager = subscriptionManager(listOf("sessionAlias"), listOf(Direction.FIRST))

        handler respondsWith { subscriptionManager.handler(randomString(), messages[0].toBatch()) }

        Context(handler, subscriptionManager, requestContext) `do` {
            send(messages[0], "sessionAlias")

            resultBuilder.setSingleMessage(
                receive(TestMessageType.ORDER_CANCEL_REJECT.typeName, "sessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.ORDER_CANCEL_REJECT.typeName) { this.sequence == 1L }
                    failOn(TestMessageType.ORDER_CANCEL_REJECT.typeName) { this.sequence == 2L }
                }
            )

            send(messages[1], "sessionAlias", cleanBuffer = true)

            handler respondsWith { subscriptionManager.handler(randomString(), messages[1].toBatch()) }

            resultBuilder.setSingleMessage(
                receive(TestMessageType.NEW_ORDER_SINGLE.typeName, "sessionAlias", Direction.FIRST, 2_000) {
                    passOn(TestMessageType.NEW_ORDER_SINGLE.typeName) { this.sequence == 2L }
                    failOn(TestMessageType.NEW_ORDER_SINGLE.typeName) { this.sequence == 1L }
                }
            )
        }
        assertEquals(messages[0], resultBuilder.getMessage(0))
        assertEquals(messages[1], resultBuilder.getMessage(1))
    }

    @Test
    fun `test for ReceiveBuilder`() {
        val receiveBuilder = ReceiveBuilder(
            messageBuild(
                TestMessageType.NEW_ORDER_SINGLE.typeName,
                "sessionAlias",
                Direction.FIRST,
                1L
            ).build()
        )
        assertEquals(
            true,
            receiveBuilder.passOn(TestMessageType.NEW_ORDER_SINGLE.typeName) { this.sequence == 1L && this.sessionAlias == "sessionAlias" })
        assertEquals(
            false,
            receiveBuilder.passOn(TestMessageType.NEW_ORDER_SINGLE.typeName) { this.sequence == 2L && this.sessionAlias == "anotherSessionAlias" })
        assertEquals(
            false,
            receiveBuilder.failOn(TestMessageType.NEW_ORDER_SINGLE.typeName) { this.sequence == 1L && this.sessionAlias == "sessionAlias" })
        assertEquals(
            true,
            receiveBuilder.failOn(TestMessageType.NEW_ORDER_SINGLE.typeName) { this.sequence == 2L && this.sessionAlias == "anotherSessionAlias" })
    }
}