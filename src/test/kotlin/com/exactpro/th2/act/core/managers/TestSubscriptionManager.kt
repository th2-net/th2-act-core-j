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
package com.exactpro.th2.act.core.managers

import com.exactpro.th2.act.of
import com.exactpro.th2.act.randomMessage
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.toBatch
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.schema.message.DeliveryMetadata
import com.exactpro.th2.common.schema.message.MessageListener
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class TestSubscriptionManager {

    /**
     * Returns a mock [MessageBatchListener] that just runs when it's handler method is invoked. The name of the mock
     * can optionally be specified.
     */
    fun getMockListener(name: String? = null): MessageBatchListener {
        return if (name == null) {
            mockk { justRun { handle(any(), any()) } }
        } else {
            mockk(name) { justRun { handle(any(), any()) } }
        }
    }

    lateinit var subscriptionManager: SubscriptionManager

    @BeforeEach
    internal fun setUp() {
        subscriptionManager = SubscriptionManager()
    }

    @ParameterizedTest
    @EnumSource(value = Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should correctly distribute batch`(message_direction: Direction) {
        val listeners: Map<Direction, MessageBatchListener> = mapOf(
            Direction.FIRST to getMockListener("First Direction Mock"),
            Direction.SECOND to getMockListener("Second Direction Mock")
        )

        listeners.forEach { (direction, listener) -> subscriptionManager.register(direction, listener) }

        val consumerTag = DeliveryMetadata(randomString())
        val messageBatch = randomMessage(direction = message_direction).toBatch()

        subscriptionManager.handle(consumerTag, messageBatch)

        listeners.map { (direction, listener) ->
            if (direction == message_direction) {
                verify { listener.handle(consumerTag, messageBatch) }
            } else {
                verify(exactly = 0) { listener.handle(any(), any()) }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should correctly register multiple listeners`(direction: Direction) {
        val listeners = 10 of { mockk<MessageBatchListener> { } }

        listeners.forEach { subscriptionManager.register(direction, it) }
    }

    @Test
    fun `test should correctly handle empty batch`() {
        val listeners: Map<Direction, MessageBatchListener> = mapOf(
            Direction.FIRST to getMockListener(),
            Direction.SECOND to getMockListener()
        )

        listeners.forEach { (direction, listener) -> subscriptionManager.register(direction, listener) }

        val consumerTag = DeliveryMetadata(randomString())
        val messageBatch = MessageBatch.getDefaultInstance()

        subscriptionManager.handle(consumerTag, messageBatch)

        listeners.values.map { verify(exactly = 0) { it.handle(any(), any()) } }
    }

    @Test
    fun `test should correctly handle batch with unsupported direction`() {
        val listeners: Map<Direction, MessageListener<MessageBatch>> = mapOf(
            Direction.FIRST to getMockListener(),
            Direction.SECOND to getMockListener()
        )

        listeners.forEach { (direction, listener) -> subscriptionManager.register(direction, listener) }

        // A Spy is used here because Direction.UNRECOGNIZED cannot be set for the real object.
        val messageID: MessageID = spyk { }
        every { messageID.direction } returns Direction.UNRECOGNIZED

        val message = Message
                .newBuilder()
                .setMetadata(MessageMetadata.newBuilder().setId(messageID).build())
                .build()

        val consumerTag = DeliveryMetadata(randomString())
        val messageBatch = message.toBatch()

        subscriptionManager.handle(consumerTag, messageBatch)

        listeners.values.forEach { verify(exactly = 0) { it.handle(any(), any()) } }
    }

    @ParameterizedTest
    @EnumSource(value = Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should unregister listener for direction`(direction: Direction) {
        val listener = getMockListener()

        subscriptionManager.register(direction, listener)
        subscriptionManager.unregister(direction, listener)

        val consumerTag = DeliveryMetadata(randomString())
        val messageBatch = randomMessage(direction = direction).toBatch()

        subscriptionManager.handle(consumerTag, messageBatch)

        verify(exactly = 0) { listener.handle(any(), any()) }
    }
}
