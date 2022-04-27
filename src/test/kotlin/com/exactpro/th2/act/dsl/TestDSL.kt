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

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.dsl.*
import com.exactpro.th2.act.core.handlers.decorators.TestSystemResponseReceiver
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sessionAlias
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isSameInstanceAs

class TestDSL {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var messageRouter: MessageRouter
    private lateinit var eventRouter: EventRouter

    private lateinit var handler:  TestSystemResponseReceiver.StubRequestHandler

    @BeforeEach
    internal fun setUp() {
        handler = spyk { }
        val messageBatchRouter: StubMessageRouter<MessageBatch> = StubMessageRouter()
        messageRouter = MessageRouter(messageBatchRouter)
        val eventBatchRouter: StubMessageRouter<EventBatch> = StubMessageRouter()
        eventRouter = EventRouter(eventBatchRouter)
    }

    @Test
    fun `send message and wait echo`() {
        val connectionID = ConnectionID.newBuilder().setSessionAlias("sessionAlias").build()
        val parentEventId = EventID.newBuilder().setId("eventId").build()
        val metadataOne = MessageMetadata.newBuilder()
            .setMessageType(TestMessageType.NEW_ORDER_SINGLE.typeName)
            .setId(MessageID.newBuilder().setConnectionId(connectionID).setDirection(Direction.FIRST))

        val metadataTwo = MessageMetadata.newBuilder()
            .setMessageType(TestMessageType.NEW_ORDER_SINGLE.typeName)
            .setId(MessageID.newBuilder().setConnectionId(connectionID).setDirection(Direction.SECOND))

        val messages = listOf<Message>(Message.newBuilder().setMetadata(metadataOne).setParentEventId(parentEventId).build(),
            Message.newBuilder().setMetadata(metadataTwo).setParentEventId(parentEventId).build())

        subscriptionManager = SubscriptionManager(listOf("sessionAlias"), listOf(Direction.FIRST, Direction.SECOND))
        handler respondsWith {
                subscriptionManager.handler(randomString(), messages[1].toBatch())
        }

        var echo: Message = Message.getDefaultInstance()
        Context(handler, subscriptionManager, messageRouter, eventRouter) `do` {
            echo = send(messages[0], "sessionAlias", Direction.FIRST, true)
        }

        expect {
            that(echo).isSameInstanceAs(messages[1])
        }
    }

    @Test
    fun `test should receive response message`() {

        val expectedMessage = Message.newBuilder().apply {
            this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
            this.sessionAlias = "sessionAlias"
            this.direction = Direction.FIRST
            this.parentEventId = EventID.newBuilder().setId("eventId").build()
        }.build()

        val messages = mutableListOf<Message>(expectedMessage)

        subscriptionManager = listOf("sessionAlias", "anotherSessionAlias").subscriptionManager(listOf(Direction.FIRST))

        handler respondsWith {
            messages.forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        var receivedMessage = Message.getDefaultInstance()
        Context(handler, subscriptionManager, messageRouter, eventRouter) `do` {
            send(expectedMessage, "sessionAlias", Direction.FIRST)
            receivedMessage = receive("NewOrderSingle", expectedMessage.parentEventId, "sessionAlias", Direction.FIRST) {
                passOn("NewOrderSingle") {
                    direction == Direction.FIRST
                }
                failOn("NewOrderSingle") {
                    direction == Direction.SECOND
                }
            }
        }

        expect {
            that(receivedMessage).isSameInstanceAs(expectedMessage)
        }
    }

    @Test
    fun `repeat until`() {
        val expectedMessage = Message.newBuilder().apply {
            this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
            this.sessionAlias = "sessionAlias"
            this.direction = Direction.FIRST
            this.parentEventId = EventID.newBuilder().setId("eventId").build()
        }.build()

        val messages = mutableListOf<Message>(Message.newBuilder().apply {
            this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
            this.sessionAlias = "sessionAlias"
            this.direction = Direction.FIRST
            this.parentEventId = EventID.newBuilder().setId("eventId").build()
        }.build(),
            Message.newBuilder().apply {
            this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
            this.sessionAlias = "sessionAlias"
            this.direction = Direction.FIRST
            this.parentEventId = EventID.newBuilder().setId("eventId2").build()
        }.build(),
            Message.newBuilder().apply {
                this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
                this.sessionAlias = "sessionAlias"
                this.direction = Direction.FIRST
                this.parentEventId = EventID.newBuilder().setId("eventId2").build()
            }.build())


        subscriptionManager = SubscriptionManager(listOf("sessionAlias", "anotherSessionAlias"), listOf(Direction.FIRST))

        handler respondsWith {
            messages.forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        Context(handler, subscriptionManager, messageRouter, eventRouter) `do` {
            send(expectedMessage, "sessionAlias", Direction.FIRST)

           repeatUntil { mes ->
                mes.sessionAlias == "sessionAlias"
            } `do` {
                receive("NewOrderSingle", expectedMessage.parentEventId,"sessionAlias", Direction.FIRST) { //TODO
                    passOn("NewOrderSingle") {
                        direction == Direction.FIRST
                    }
                    failOn("NewOrderSingle") {
                        direction == Direction.SECOND
                    }
                }
            }
        }
    }

    @Test
    fun `text CheckRule`() {
        val messages = mutableListOf<Message>(Message.newBuilder().apply {
            this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
            this.sessionAlias = "sessionAlias"
            this.direction = Direction.FIRST
            this.parentEventId = EventID.newBuilder().setId("eventId").build()
        }.build(),
            Message.newBuilder().apply {
                this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
                this.sessionAlias = "anotherSessionAlias"
                this.direction = Direction.FIRST
                this.parentEventId = EventID.newBuilder().setId("eventId").build()
            }.build(),
            Message.newBuilder().apply {
                this.messageType = TestMessageType.NEW_ORDER_SINGLE.typeName
                this.sessionAlias = "sessionAlias"
                this.direction = Direction.FIRST
                this.parentEventId = EventID.newBuilder().setId("eventId2").build()
            }.build())

        val checkRule = CheckRule(ConnectionID.newBuilder().setSessionAlias("sessionAlias").build())

        expect {
            that(checkRule.onMessage(messages[0])).isSameInstanceAs(true)
            that(checkRule.onMessage(messages[1])).isSameInstanceAs(false)
            that(checkRule.onMessage(messages[2])).isSameInstanceAs(true)
        }
    }
}