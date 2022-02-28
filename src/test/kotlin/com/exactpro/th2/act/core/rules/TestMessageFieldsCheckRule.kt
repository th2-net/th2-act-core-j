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

package com.exactpro.th2.act.core.rules

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.messages.IField
import com.exactpro.th2.common.grpc.ConnectionID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expect
import strikt.assertions.*

internal class TestMessageFieldsCheckRule {

    /* Test List for MessageFieldsCheckRule:
     * 1) Should throw an error if created with a blank connection ID. V
     * 2) Should create new MessageFieldsCheckRule when all MessageFields are for different message types. V
     * 3) Should throw runtime error when multiple MessageFields are specified for the same message type. V
     * 4) Should match all messages with correct connection ID, message type and fields. V
     * 5) Should not match messages with incorrect connection ID, message type or fields. V
     * 6) Should return correct response message when matched. V
     * 7) Should return null when no message has been matched. V
     * 8) Should return processed message IDs of all processed messages with correct connection ID. V
     * 9) Should not add save message IDs of messages with a different connection ID. V
     */

    /**
     * Returns a common connection ID (used for convenient to omit the details of the ID).
     */
    private fun someCommonConnectionID(): ConnectionID = "Test Alias".toConnectionID()

    /**
     * Returns an array of common message fields (used for convenience to omit the details of the fields).
     */
    private fun someCommonFields(): Array<Pair<IField, String>> = arrayOf(
        TestField.CLIENT_ORDER_ID to "Test ID 1", TestField.DISPLAY_QTY to "Test Quantity 1"
    )

    @Test
    fun `test should throw an error when a blank connection is specified`() {
        assertThrows<IllegalArgumentException> {
            MessageFieldsCheckRule("".toConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields())
        }
    }

    @Test
    fun `test should create rule when all message types are different`() {
        MessageFieldsCheckRule(
            randomConnectionID(),
            TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields(),
            TestMessageType.EXECUTION_REPORT.toRandomMessageFields()
        )
    }

    @Test
    fun `test should throw error when duplicate message types are specified`() {
        assertThrows<RuntimeException> {
            MessageFieldsCheckRule(
                randomConnectionID(),
                TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields(),
                TestMessageType.EXECUTION_REPORT.toRandomMessageFields(),
                TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields()
            )
        }
    }

    @Test
    fun `test should match message with correct connection ID, type and fields`() {
        val checkRule = MessageFieldsCheckRule(
            someCommonConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toMessageFields(*someCommonFields())
        )

        val expectedMessage = TestMessageType.NEW_ORDER_SINGLE.toMessage(
            someCommonConnectionID(), TestField.PRICE to "Test Price 1", *someCommonFields()
        )
        expect {
            that(checkRule.onMessage(expectedMessage)).isTrue()
        }
    }

    @Test
    fun `test should match message when multiple message types are expected`() {
        val checkRule = MessageFieldsCheckRule(
            someCommonConnectionID(),
            TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields(),
            TestMessageType.TRADE_CAPTURE_REPORT.toRandomMessageFields(),
            TestMessageType.EXECUTION_REPORT.toMessageFields(*someCommonFields())
        )

        val expectedMessage = TestMessageType.EXECUTION_REPORT.toMessage(someCommonConnectionID(), *someCommonFields())

        expect {
            that(checkRule.onMessage(expectedMessage)).isTrue()
        }
    }

    @Test
    fun `test should not match message with incorrect connection ID`() {
        val checkRule = MessageFieldsCheckRule(
            "Test Alias".toConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields()
        )

        val unexpectedMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage("Incorrect Test Alias".toConnectionID())

        expect {
            that(checkRule.onMessage(unexpectedMessage)).isFalse()
        }
    }

    @Test
    fun `test should not match message with correct connection ID and incorrect type`() {
        val checkRule = MessageFieldsCheckRule(
            someCommonConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields()
        )

        val unexpectedMessage = TestMessageType.EXECUTION_REPORT.toRandomMessage(someCommonConnectionID())

        expect {
            that(checkRule.onMessage(unexpectedMessage)).isFalse()
        }
    }

    @Test
    fun `test should not match message with correct connection ID and type, and incorrect field values`() {
        val checkRule = MessageFieldsCheckRule(
            someCommonConnectionID(),
            TestMessageType.NEW_ORDER_SINGLE.toMessageFields(
                TestField.CLIENT_ORDER_ID to "Test ID 1",
                TestField.DISPLAY_QTY to "Test Quantity 1"
            )
        )

        val unexpectedMessage = TestMessageType.NEW_ORDER_SINGLE.toMessage(
            someCommonConnectionID(),
            TestField.CLIENT_ORDER_ID to "Test ID 1",
            TestField.DISPLAY_QTY to "Wrong Test Quantity"
        )
        expect {
            that(checkRule.onMessage(unexpectedMessage)).isFalse()
        }
    }

    @Test
    fun `test should return correct response message when matched`() {
        val checkRule = MessageFieldsCheckRule(
            someCommonConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toMessageFields(*someCommonFields())
        )

        val expectedMessage = TestMessageType.NEW_ORDER_SINGLE.toMessage(someCommonConnectionID(), *someCommonFields())

        val unexpectedMessages = Array(10) {
            TestMessageType.values().random().toRandomMessage(someCommonConnectionID())
        }

        unexpectedMessages.slice(0..5).forEach { checkRule.onMessage(it) }
        checkRule.onMessage(expectedMessage)
        unexpectedMessages.slice(5..9).forEach { checkRule.onMessage(it) }

        expect {
            that(checkRule.response).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should return null when no message has been matched`() {
        val checkRule = MessageFieldsCheckRule(
            someCommonConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields()
        )

        val unexpectedMessages = Array(10) {
            TestMessageType.values().filter { it != TestMessageType.NEW_ORDER_SINGLE } // The message type will never match.
                    .random().toRandomMessage(someCommonConnectionID())
        }

        unexpectedMessages.forEach { checkRule.onMessage(it) }

        expect {
            that(checkRule.response).isNull()
        }
    }

    @Test
    fun `test should return message IDs of all messages with expected connection ID`() {
        val checkRule = MessageFieldsCheckRule(
            "Test Alias".toConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields()
        )

        val matchingMessages = Array(10) {
            TestMessageType.values().random().toRandomMessage("Test Alias".toConnectionID())
        }

        val unexpectedMessages = Array(10) {
            TestMessageType.values().random().toRandomMessage("Wrong Test Alias".toConnectionID())
        }

        matchingMessages.plus(unexpectedMessages).forEach { checkRule.onMessage(it) }

        expect {
            that(checkRule.processedIDs()).containsExactlyInAnyOrder(matchingMessages.map { it.metadata.id })
        }
    }

    @Test
    fun `test should return an empty list of processed message IDs`() {
        val checkRule = MessageFieldsCheckRule(
            "Test Alias".toConnectionID(), TestMessageType.NEW_ORDER_SINGLE.toRandomMessageFields()
        )

        val unexpectedMessages = Array(10) {
            TestMessageType.values().random().toRandomMessage("Wrong Test Alias".toConnectionID())
        }

        unexpectedMessages.forEach { checkRule.onMessage(it) }

        expect {
            that(checkRule.processedIDs()).isEmpty()
        }
    }
}
