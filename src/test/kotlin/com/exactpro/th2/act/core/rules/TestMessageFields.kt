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

import com.exactpro.th2.act.TestField
import com.exactpro.th2.act.TestMessageType
import com.exactpro.th2.act.core.messages.IField
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.act.randomMessageTypes
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageMetadata
import com.exactpro.th2.common.value.toValue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.util.stream.Stream

internal class TestMessageFields {

    /* Test List for MessageFields:
     * 1) Should return the message type that it matches correctly. V
     * 2) Should match messages with the same message type and expected field values. V
     * 3) Should not match messages with different message types. V
     * 4) Should not match messages with field values different from the ones expected. V
     * 5) Should match messages with expected nested field values. ? No requirement
     * 6) Should not match messages with nested field values different from the ones expected. ? No requirement
     */

    private fun IMessageType.withFields(fields: Map<IField, String>): Message {
        return this.withFields(*fields.entries.map { it.key to it.value }.toTypedArray())
    }

    private fun IMessageType.withFields(vararg field: Pair<IField, String>): Message = Message
            .newBuilder()
            .setMetadata(MessageMetadata.newBuilder().setMessageType(this.typeName))
            .putAllFields(field.associate { entry -> entry.first.fieldName to entry.second.toValue() })
            .build()

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should return correct message type`(messageType: IMessageType) {
        val messageFields = MessageFields(messageType, mapOf())

        expect {
            that(messageFields.messageType).isEqualTo(messageType)
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should match message with matching type and exact field values`(messageType: IMessageType) {
        val fieldMap: Map<IField, String> = mapOf(
            TestField.CLIENT_ORDER_ID to "Test ID", TestField.ORDER_TYPE to "Test Order Type"
        )

        val messageFields = MessageFields(messageType, fieldMap)

        expect {
            that(messageFields.match(messageType.withFields(fieldMap))).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should match message with matching type and extra field values`(messageType: IMessageType) {
        val fieldMap: Map<IField, String> = mapOf(
            TestField.CLIENT_ORDER_ID to "Test ID",
            TestField.ORDER_TYPE to "Test Order Type"
        )

        val messageFields = MessageFields(messageType, fieldMap)

        val message = messageType.withFields(
            fieldMap.plus(
                mapOf(
                    TestField.PRICE to "100.000",
                    TestField.DISPLAY_QTY to "200000"
                )
            )
        )

        expect {
            that(messageFields.match(message)).isTrue()
        }
    }

    @ParameterizedTest
    @MethodSource("provideDistinctMessageTypes")
    fun `test should not match message with different type`(firstType: IMessageType, secondType: IMessageType) {
        val messageFields = MessageFields(firstType, mapOf())

        require(firstType != secondType)

        expect {
            that(messageFields.match(secondType.withFields(mapOf()))).isFalse()
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should match message with different field values`(messageType: IMessageType) {
        val fieldMap: Map<IField, String> = mapOf(
            TestField.CLIENT_ORDER_ID to "Test ID",
            TestField.ORDER_TYPE to "Test Order Type"
        )

        val messageFields = MessageFields(messageType, fieldMap)

        val message = messageType.withFields(
            TestField.CLIENT_ORDER_ID to "Wrong Test ID",
            TestField.ORDER_TYPE to "Test Order Type"
        )

        expect {
            that(messageFields.match(message)).isFalse()
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should match message with missing fields`(messageType: IMessageType) {
        val fieldMap: Map<IField, String> = mapOf(
            TestField.CLIENT_ORDER_ID to "Test ID",
            TestField.ORDER_TYPE to "Test Order Type"
        )

        val messageFields = MessageFields(messageType, fieldMap)

        val message = messageType.withFields(
            TestField.BENCHMARK_SECURITY_ID to "Not important", // No Client Order ID
            TestField.ORDER_TYPE to "Test Order Type"
        )

        expect {
            that(messageFields.match(message)).isFalse()
        }
    }

    @Disabled("Because currently checking nested fields in a message is not a requirement.")
    @Test
    fun `test should match message with expected nested field values`() {
        TODO("Implement me.")
    }

    @Disabled("Because currently checking nested fields in a message is not a requirement.")
    @Test
    fun `test should not match message with different nested field values`() {
        TODO("Implement me.")
    }

    companion object {

        @JvmStatic
        @SuppressWarnings("unused")
        private fun provideDistinctMessageTypes(): Stream<Arguments> {
            return 3.randomMessageTypes().flatMap { firstType ->
                3.randomMessageTypes()
                        .filter { secondType -> firstType != secondType }
                        .map { secondType -> Arguments.of(firstType, secondType) }
            }.stream().limit(3)
        }
    }
}
