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

import com.exactpro.th2.act.TestMessageType
import com.exactpro.th2.act.randomMessageTypes
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.util.stream.Stream

internal class TestMessageMapping {

    /* Test List for MessageMapping:
     * 1) Should match message types that contain all types from this mapping if not exact. V
     * 2) Should not match message types that do not contain all types from this message mapping. V
     * 3) Should match message types that exactly contain all types from this mapping if exact. V
     * 4) Should not match message types that do not exactly contain all types from this mapping if exact. V
     */

    @ParameterizedTest
    @MethodSource("provideNonExactMappings")
    fun `test should match message types without exact mapping`(
        mappingMessageTypes: List<IMessageType>, mapping: MessageMapping
    ) {

        val messageTypes = 10.randomMessageTypes().plus(mappingMessageTypes)

        expect {
            that(mapping.matches(messageTypes.map { it.typeName })).describedAs(
                        """
                        Tested Message Types: $messageTypes
                        Mapping Message Types: $mappingMessageTypes
                        """
                    ).isTrue()
        }
    }

    @ParameterizedTest
    @MethodSource("provideMappings")
    fun `test should not match message types if at least one is missing`(
        mappingMessageTypes: List<IMessageType>, mapping: MessageMapping
    ) {
        val missingMessageType = mappingMessageTypes.random()
        val messageTypes = 10.randomMessageTypes().filter { it != missingMessageType }

        expect {
            that(mapping.matches(messageTypes.map { it.typeName })).describedAs(
                        """
                        Tested Message Types: $messageTypes
                        Mapping Message Types: $mappingMessageTypes
                        """
                    ).isFalse()
        }
    }

    @ParameterizedTest
    @MethodSource("provideExactMappings")
    fun `test should match message types with exact mapping`(
        mappingMessageTypes: List<IMessageType>, mapping: MessageMapping
    ) {
        expect {
            that(mapping.matches(mappingMessageTypes.map { it.typeName })).isTrue()
        }
    }

    @ParameterizedTest
    @MethodSource("provideExactMappings")
    fun `test should not match message types with extra or missing types with exact mapping`(
        mappingMessageTypes: List<IMessageType>, mapping: MessageMapping
    ) {
        val extraMessageTypes = 10.randomMessageTypes().plus(mappingMessageTypes)
        val missingMessageTypes = mappingMessageTypes.minus(mappingMessageTypes.random())

        expect {
            that(mapping.matches(extraMessageTypes.map { it.typeName })).describedAs(
                        """
                        Tested Message Types: $extraMessageTypes
                        Mapping Message Types: $mappingMessageTypes
                        """
                    ).isFalse()

            that(mapping.matches(missingMessageTypes.map { it.typeName })).describedAs(
                        """
                        Tested Message Types: $missingMessageTypes
                        Mapping Message Types: $mappingMessageTypes
                        """
                    ).isFalse()
        }
    }

    companion object {

        @JvmStatic
        @SuppressWarnings("unused")
        private fun provideExactMappings(): Stream<Arguments> {
            val mappingMessageTypes = listOf(
                listOf(TestMessageType.NEW_ORDER_SINGLE),
                listOf(TestMessageType.NEW_ORDER_SINGLE, TestMessageType.NEW_ORDER_SINGLE),
                listOf(TestMessageType.NEW_ORDER_SINGLE, TestMessageType.EXECUTION_REPORT)
            )

            return Stream.concat(
                mappingMessageTypes.map { Arguments.of(it, passedOnExact(*it.toTypedArray())) }.stream(),
                mappingMessageTypes.map { Arguments.of(it, failedOnExact(*it.toTypedArray())) }.stream()
            )
        }

        @JvmStatic
        @SuppressWarnings("unused")
        private fun provideNonExactMappings(): Stream<Arguments> {
            val mappingMessageTypes = listOf(
                listOf(TestMessageType.REJECT, TestMessageType.TRADE_CAPTURE_REPORT, TestMessageType.ORDER_CANCEL_REQUEST),
                listOf(TestMessageType.NEW_ORDER_SINGLE, TestMessageType.EXECUTION_REPORT)
            )

            return Stream.concat(
                mappingMessageTypes.map { Arguments.of(it, passedOn(*it.toTypedArray())) }.stream(),
                mappingMessageTypes.map { Arguments.of(it, failedOn(*it.toTypedArray())) }.stream()
            )
        }

        @JvmStatic
        @SuppressWarnings("unused")
        private fun provideMappings(): Stream<Arguments> {
            return Stream.concat(provideExactMappings(), provideNonExactMappings())
        }
    }
}
