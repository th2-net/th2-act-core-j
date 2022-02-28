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

import com.exactpro.th2.act.*
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.value.toValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

internal class TestWrapMessageFieldsKt {

    /* Test List for WrapMessageFields:
     * 1) Should wrap a flat map of fields. V
     * 2) Should specify correct message type for wrapped message. V
     * 3) Should throw NullPointerException if the key in a map is null. V
     * 4) Should throw RuntimeException if the key in a map is not a IField instance. V
     * 5) Should throw NullPointerException if the value in a map is null. V
     * 6) Should throw RuntimeException if the value in a map is not an instance of a String, Number, Map or Iterable. V
     * 7) Should wrap a nested map of fields. V
     * 8) Should wrap a nested list of maps. V
     * 9) Should wrap a nested list of simple values.
     * 10) Should throw an IllegalArgumentException if the value in a nested list is not an instance of a map. V
     */

    @Test
    fun `test should wrap a flat map of fields`() {
        val fields = mapOf(
            TestField.ORDER_QTY to 23000,
            TestField.PRICE to 122.123,
            TestField.CLIENT_ORDER_ID to "Some ID"
        )

        expect {
            that(fields.toMessage()).isEqualTo(
                Message.newBuilder().putAllFields(
                    mapOf(
                        TestField.ORDER_QTY.fieldName to 23000.toValue(),
                        TestField.PRICE.fieldName to 122.123.toValue(),
                        TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                    )
                ).build()
            )
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should add correct message type to the message metadata`(messageType: IMessageType) {
        val fields = 10.randomFields().toMap()

        expect {
            that(fields.toMessage(messageType).messageType).isEqualTo(messageType.typeName)
        }
    }

    @Test
    fun `test should throw exception if key in field map is null`() {
        val fields = 10.randomFields().toList().plus(null to randomString()).shuffled().toMap()

        expectThrows<NullPointerException> { fields.toMessage() }
    }

    @Test
    fun `test should throw exception if key in a field map is not a field`() {
        val fields = 10.randomFields().toList().plus("Not a Field" to randomString()).shuffled().toMap()

        expectThrows<RuntimeException> { fields.toMessage() }
    }

    @Test
    fun `test should throw exception if value in field map is null`() {
        val fields = 10.randomFields().toList().plus(randomField() to null).toMap()

        expectThrows<NullPointerException> { fields.toMessage() }
    }

    @Test
    fun `test should throw exception if value in field map is not an instance of a valid class`() {
        listOf(TestMessageType.REJECT, Message.getDefaultInstance(), this).forEach { randomClassInstance ->

            // NOTE: Make sure that the field with the incorrect value is actually present in the map.
            val fields = 10.randomFields().toList().plus(randomField() to randomClassInstance).toMap()

            expectThrows<RuntimeException> { fields.toMessage() }
        }
    }

    @Test
    fun `test should wrap a nested map of fields`() {
        val fields = mapOf(
            TestField.ORDER_QTY to 23000,
            TestField.NO_PARTY_IDS to mapOf(
                TestField.PRICE to 122.123,
                TestField.CLIENT_ORDER_ID to "Some ID"
            )
        )

        val expectedMessage = Message.newBuilder().putAllFields(
            mapOf(
                TestField.ORDER_QTY.fieldName to 23000.toValue(),
                TestField.NO_PARTY_IDS.fieldName to Message.newBuilder().putAllFields(
                    mapOf(
                        TestField.PRICE.fieldName to 122.123.toValue(),
                        TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                    )
                ).build().toValue()
            )
        ).build()

        expect {
            that(fields.toMessage()).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should wrap a nested list of field maps`() {
        val fields = mapOf(
            TestField.ORDER_QTY to "23000",
            TestField.NO_PARTY_IDS to listOf(
                mapOf(
                    TestField.PRICE to 122.123,
                    TestField.CLIENT_ORDER_ID to "Some ID"
                )
            )
        )

        val expectedMessage = Message.newBuilder().putAllFields(
            mapOf(
                TestField.ORDER_QTY.fieldName to "23000".toValue(),
                TestField.NO_PARTY_IDS.fieldName to ListValue.newBuilder().addValues(
                    Message.newBuilder().putAllFields(
                        mapOf(
                            TestField.PRICE.fieldName to "122.123".toValue(),
                            TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                        )
                    ).build().toValue()
                ).build().toValue()
            )
        ).build()

        expect {
            that(fields.toMessage()).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should wrap a nested list of a list of field maps`() {
        val fields = mapOf(
            TestField.ORDER_QTY to "23000",
            TestField.TRADE_REPORT_ID to listOf(
                mapOf(
                    TestField.NO_PARTY_IDS to listOf(
                        mapOf(
                            TestField.PRICE to "122.123",
                            TestField.CLIENT_ORDER_ID to "Some ID"
                        )
                    )
                )
            )
        )

        val expectedMessage = Message.newBuilder().putAllFields(
            mapOf(
                TestField.ORDER_QTY.fieldName to "23000".toValue(),
                TestField.TRADE_REPORT_ID.fieldName to ListValue.newBuilder().addValues(
                    Message.newBuilder().putAllFields(
                        mapOf(
                            TestField.NO_PARTY_IDS.fieldName to ListValue.newBuilder().addValues(
                                Message.newBuilder().putAllFields(
                                    mapOf(
                                        TestField.PRICE.fieldName to "122.123".toValue(),
                                        TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                                    )
                                ).build().toValue()
                            ).build().toValue()
                        )
                    ).build().toValue()
                ).build().toValue()
            )
        ).build()

        expect {
            that(fields.toMessage()).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should wrap a nest list of simple values`() {
        val fields = mapOf(
            TestField.ORDER_QTY to "23000",
            TestField.TRADE_REPORT_ID to listOf(
                "122.123",
                "Some ID",
                1005,
                2.23e12
            )
        )

        val expectedMessage = Message.newBuilder().putAllFields(
            mapOf(
                TestField.ORDER_QTY.fieldName to "23000".toValue(),
                TestField.TRADE_REPORT_ID.fieldName to ListValue.newBuilder().addAllValues(
                    listOf(
                        "122.123".toValue(),
                        "Some ID".toValue(),
                        1005.toValue(),
                        2.23e12.toValue()
                    )
                ).build().toValue()
            )
        ).build()

        expect {
            that(fields.toMessage()).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should throw exception if a nested list value is not a field map or simple value`() {
        listOf(TestMessageType.REJECT, Message.getDefaultInstance(), this).forEach { randomClassInstance ->

            val fields = 10.randomFields().toList().plus(randomField() to listOf(randomClassInstance)).toMap()

            assertThrows<RuntimeException> { fields.toMessage() }
        }
    }

}
