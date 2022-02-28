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

package com.exactpro.th2.act.core.receivers

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo

internal class TestMessageReceiver {

    /* Test List for MessageReceiver:
     * 1) Should create MessageReceiver from monitor, subscription manager, check rule and direction. V
     * 2) Should match message to rule and notify response monitor. V
     * 3) Should not notify response monitor if no message was matched. V
     * 4) Should return correct response message when matched. V
     * 5) Should return message IDs of all messages with a connection ID matching the one in the rule. V
     * 6) Should unregister message listener once closed. V
     */

    /**
     * Returns a [MessageReceiver] created with the specified [ICheckRule] for this direction.
     */
    private fun Direction.receiver(checkRule: ICheckRule): MessageReceiver {
        return MessageReceiver(subscriptionManager, responseMonitor, checkRule, this)
    }

    /**
     * Returns a common connection ID (used for convenient to omit the details of the ID).
     */
    private fun commonConnectionID(): ConnectionID = "Common Test Alias".toConnectionID()


    private lateinit var responseMonitor: IMessageResponseMonitor
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeEach
    internal fun setUp() {
        responseMonitor = mockk { justRun { responseReceived() } }
        subscriptionManager = spyk { }
    }

    @ParameterizedTest
    @EnumSource(Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should create message receiver`(direction: Direction) {
        MessageReceiver(
            subscriptionManager = subscriptionManager,
            monitor = responseMonitor,
            checkRule = randomConnectionID().toRandomMessageFieldsCheckRule(),
            direction = direction
        )
    }

    @ParameterizedTest
    @EnumSource(Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should match message to rule and notify response monitor`(direction: Direction) {
        val rule = commonConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val messages = 10 of { randomMessage(commonConnectionID(), direction = direction) }
        val matchingMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage(commonConnectionID(), direction = direction)

        direction.receiver(rule).use {
            messages.plus(matchingMessage).forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        verify { responseMonitor.responseReceived() }
    }

    @ParameterizedTest
    @EnumSource(Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should not notify response monitor when no message is matched`(direction: Direction) {
        val rule = commonConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val messages = 10.randomMessageTypes()
                .filter { it != TestMessageType.NEW_ORDER_SINGLE }
                .map { it.toRandomMessage(commonConnectionID(), direction = direction) }

        direction.receiver(rule).use {
            messages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        verify(exactly = 0) { responseMonitor.responseReceived() }
    }

    @Test
    fun `test should return correct response message when matched`() {
        val rule = commonConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val expectedMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage(
            commonConnectionID(), direction = Direction.FIRST
        )

        val randomMessages = 10.randomMessageTypes()
                .filter { it != TestMessageType.NEW_ORDER_SINGLE }
                .map { it.toRandomMessage(commonConnectionID()) }

        val receiver = Direction.FIRST.receiver(rule)

        receiver.use {
            randomMessages.plus(expectedMessage)
                    .shuffled()
                    .forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        expect {
            that(receiver.responseMessages).isEqualTo(listOf(expectedMessage))
        }
    }

    @Test
    fun `test should return an empty list when no message is matched`() {
        val rule = commonConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val randomMessages = 10.randomMessageTypes()
                .filter { it != TestMessageType.NEW_ORDER_SINGLE }
                .map { it.toRandomMessage(commonConnectionID()) }

        val receiver = Direction.FIRST.receiver(rule)

        receiver.use {
            randomMessages.forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
        }

        expect {
            that(receiver.responseMessages).isEqualTo(listOf())
        }
    }

    @Test
    fun `test should return message IDs of all messages with matching connection IDs`() {
        val expectedMessages = 10 of { randomMessage("Test Alias 1".toConnectionID(), direction = Direction.FIRST) }
        val unexpectedMessages = 10 of { randomMessage("Test Alias 2".toConnectionID()) }

        val receiver = Direction.FIRST.receiver("Test Alias 1".toConnectionID().toRandomMessageFieldsCheckRule())

        receiver.use {
            expectedMessages.plus(unexpectedMessages).toList().shuffled().forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        expect {
            that(receiver.processedMessageIDs).containsExactlyInAnyOrder(expectedMessages.map { it.metadata.id })
        }
    }

    @ParameterizedTest
    @EnumSource(Direction::class, mode = EnumSource.Mode.EXCLUDE, names = ["UNRECOGNIZED"])
    fun `test should unregister message listeners when closed`(direction: Direction) {
        direction.receiver(randomConnectionID().toRandomMessageFieldsCheckRule()).use { }

        verify { subscriptionManager.unregister(direction, any()) }
    }
}
