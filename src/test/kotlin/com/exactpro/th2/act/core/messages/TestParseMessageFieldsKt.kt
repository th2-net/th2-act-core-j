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

package com.exactpro.th2.act.core.messages

import com.exactpro.th2.act.TestField
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

internal class TestParseMessageFieldsKt {

    /* Test List for ParseMessageFields:
     * 1) Should parse string field that is not nested. V
     * 2) Should parse string field that is nested. V
     * 4) Should throw MissingFieldException if the specified field is not present in the message. V
     */

    @Test
    fun `test should parse string that isn't nested`() {
        val testMessage = mapOf(
            TestField.ORDER_QTY to "100000",
            TestField.CLIENT_ORDER_ID to mapOf(TestField.PRE_TRADE_ANONYMITY to "Yes"),
            TestField.PRICE to "23.252"
        ).toMessage()

        expect {
            that(testMessage.getStringField(TestField.ORDER_QTY.fieldName)).isEqualTo("100000")
            that(testMessage.getStringField(TestField.PRICE.fieldName)).isEqualTo("23.252")
            that(testMessage.getStringField(TestField.ORDER_QTY)).isEqualTo("100000")
            that(testMessage.getStringField(TestField.PRICE)).isEqualTo("23.252")
        }
    }

    @Test
    fun `test should parse string that is nested`() {
        val testMessage = mapOf(
            TestField.ORDER_QTY to "100000",
            TestField.NO_PARTY_IDS to listOf(
                mapOf(
                    TestField.PARTY_ROLE to "ENTERING_TRADER",
                    TestField.PARTY_ID_SOURCE to "D",
                    TestField.PARTY_ROLE_QUALIFIER to "22",
                    TestField.TRADE_REPORT_ID to mapOf(
                        TestField.TRADE_REPORT_STATUS to "Random Status"
                    )
                )
            )
        ).toMessage()

        expect {
            that(testMessage.getStringField(TestField.PARTY_ROLE.fieldName)).isEqualTo("ENTERING_TRADER")
            that(testMessage.getStringField(TestField.PARTY_ROLE_QUALIFIER.fieldName)).isEqualTo("22")
            that(testMessage.getStringField(TestField.TRADE_REPORT_STATUS.fieldName)).isEqualTo("Random Status")
            that(testMessage.getStringField(TestField.PARTY_ROLE)).isEqualTo("ENTERING_TRADER")
            that(testMessage.getStringField(TestField.PARTY_ROLE_QUALIFIER)).isEqualTo("22")
            that(testMessage.getStringField(TestField.TRADE_REPORT_STATUS)).isEqualTo("Random Status")
        }
    }

    @Test
    fun `test should parse string that is nested from a message with multiple nested fields`() { // A Mutable map is used to preserve insertion order.
        val testMessage = mutableMapOf(
            TestField.ORDER_QTY to "100000",
            TestField.PRICE to "23.252",
            TestField.CLIENT_ORDER_ID to "Some ID",
            TestField.NO_PARTY_IDS to listOf(
                mapOf(TestField.PRE_TRADE_ANONYMITY to "Yes"),
                mapOf(TestField.PARTY_ROLE to "ENTERING_TRADER")
            ),
            TestField.TRADE_REPORT_ID to mapOf(
                TestField.TRADE_REPORT_STATUS to "Random Status"
            )
        ).toMessage()

        expect {
            that(testMessage.getStringField(TestField.PRE_TRADE_ANONYMITY.fieldName)).isEqualTo("Yes")
            that(testMessage.getStringField(TestField.PARTY_ROLE.fieldName)).isEqualTo("ENTERING_TRADER")
            that(testMessage.getStringField(TestField.TRADE_REPORT_STATUS.fieldName)).isEqualTo("Random Status")
            that(testMessage.getStringField(TestField.PRE_TRADE_ANONYMITY)).isEqualTo("Yes")
            that(testMessage.getStringField(TestField.PARTY_ROLE)).isEqualTo("ENTERING_TRADER")
            that(testMessage.getStringField(TestField.TRADE_REPORT_STATUS)).isEqualTo("Random Status")
        }
    }

    @Test
    fun `test should parse string that comes after nested fields`() { // A Mutable map is used to preserve insertion order.
        val testMessage = mutableMapOf(
            TestField.ORDER_QTY to "100000",
            TestField.NO_PARTY_IDS to listOf(
                mapOf(TestField.PRE_TRADE_ANONYMITY to "Yes")
            ),
            TestField.TRADE_REPORT_ID to mapOf(
                TestField.TRADE_REPORT_STATUS to "Random Status"
            ),
            TestField.CLIENT_ORDER_ID to "Some ID"
        ).toMessage()

        expect {
            that(testMessage.getStringField(TestField.PRE_TRADE_ANONYMITY.fieldName)).isEqualTo("Yes")
            that(testMessage.getStringField(TestField.TRADE_REPORT_STATUS.fieldName)).isEqualTo("Random Status")
            that(testMessage.getStringField(TestField.CLIENT_ORDER_ID.fieldName)).isEqualTo("Some ID")
            that(testMessage.getStringField(TestField.PRE_TRADE_ANONYMITY)).isEqualTo("Yes")
            that(testMessage.getStringField(TestField.TRADE_REPORT_STATUS)).isEqualTo("Random Status")
            that(testMessage.getStringField(TestField.CLIENT_ORDER_ID)).isEqualTo("Some ID")
        }
    }

    @Test
    fun `test should throw exception if the field is not present`() {
        val testMessage = mapOf(
            TestField.ORDER_QTY to "100000",
            TestField.PRICE to "23.252",
            TestField.CLIENT_ORDER_ID to "Some ID",
            TestField.NO_PARTY_IDS to listOf(
                mapOf(TestField.PRE_TRADE_ANONYMITY to "Yes"),
                mapOf(
                    TestField.PARTY_ROLE to "ENTERING_TRADER",
                    TestField.PARTY_ID_SOURCE to "D",
                    TestField.PARTY_ROLE_QUALIFIER to "22",
                    TestField.TRADE_REPORT_ID to mapOf(
                        TestField.TRADE_REPORT_STATUS to "Random Status"
                    )
                )
            )
        ).toMessage()

        expectThrows<MissingFieldException> { testMessage.getStringField(TestField.REF_MESSAGE_TYPE.fieldName) }
        expectThrows<MissingFieldException> { testMessage.getStringField(TestField.REF_MESSAGE_TYPE) }
    }
}
