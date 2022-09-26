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
import com.exactpro.th2.act.TestMessageType
import com.exactpro.th2.act.randomField
import com.exactpro.th2.act.randomString
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageMetadata
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.toValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.assertions.containsExactly
import strikt.assertions.getValue
import strikt.assertions.isEqualTo

internal class TestMessageBuilder {

    /* Test List for WrapMessageFields:
     * 1) Should wrap a flat map of fields. V
     * 2) Should specify correct message type for wrapped message. V
     * 7) Should wrap a nested map of fields. V
     * 8) Should wrap a nested list of maps. V
     * 9) Should wrap a nested list of simple values. V
     * 10) Should throw an IllegalArgumentException if the value in a nested list is not an instance of a map. V
     */

    @Test
    fun `test should wrap a flat map of fields`() {
        val message = message("test", ConnectionID.getDefaultInstance()) {
            body {
                TestField.ORDER_QTY.fieldName to 23000
                TestField.PRICE.fieldName to 122.123
                "ClOrdID" to "Some ID"
            }
        }

        expect {
            that(message).get { fieldsMap }.isEqualTo(
                mapOf(
                    TestField.ORDER_QTY.fieldName to 23000.toValue(),
                    TestField.PRICE.fieldName to 122.123.toValue(),
                    "ClOrdID" to "Some ID".toValue()
                )
            )
        }
    }

    @Test
    fun `test should add correct message type to the message metadata`() {
        val messageFromType = message("test", ConnectionID.getDefaultInstance()) {
        }

        expect {
            that(messageFromType.messageType).isEqualTo("test")
        }
    }

    @Test
    fun `test should add correct connection id`() {
        val message = message("test", ConnectionID.newBuilder().setSessionAlias("alias").build()) {
        }

        expect {
            that(message).get { metadata }.get { id }.get { connectionId }
                .isEqualTo(ConnectionID.newBuilder().setSessionAlias("alias").build())
        }
    }

    @Test
    fun `test should wrap a nested map of fields`() {
        val message = message("test", ConnectionID.getDefaultInstance()) {
            body {
                TestField.NO_PARTY_IDS.fieldName to message {
                    TestField.PRICE.fieldName to 122.123
                    TestField.CLIENT_ORDER_ID.fieldName to "Some ID"
                }
            }
        }

        expect {
            that(message).get { fieldsMap }
                .getValue(TestField.NO_PARTY_IDS.fieldName)
                .assertThat("has message") { it.hasMessageValue() }
                .get { messageValue }
                .get { fieldsMap }
                .isEqualTo(mapOf(
                    TestField.PRICE.fieldName to 122.123.toValue(),
                    TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                ))
        }
    }

    @Test
    fun `test should wrap a nested list of field maps`() {
        val message = message("test", ConnectionID.getDefaultInstance()) {
            body {
                TestField.NO_PARTY_IDS.fieldName to list[
                    message {
                        TestField.PRICE.fieldName to 122.123
                        TestField.CLIENT_ORDER_ID.fieldName to "Some ID"
                    }
                ]
            }
        }

        expect {
            that(message).get { fieldsMap }
                .getValue(TestField.NO_PARTY_IDS.fieldName)
                .assertThat("has collection") { it.hasListValue() }
                .get { listValue }
                .get { valuesList }
                .containsExactly(
                    Message.newBuilder().putAllFields(
                        mapOf(
                            TestField.PRICE.fieldName to "122.123".toValue(),
                            TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                        )
                    ).build().toValue(),
                )
        }
    }

    @Test
    fun `test should wrap a nested list of simple values`() {
        val message = message("test", ConnectionID.getDefaultInstance()) {
            body {
                TestField.NO_PARTY_IDS.fieldName to list["test"]
            }
        }

        expect {
            that(message).get { fieldsMap }
                .getValue(TestField.NO_PARTY_IDS.fieldName)
                .assertThat("has collection") { it.hasListValue() }
                .get { listValue }
                .get { valuesList }
                .containsExactly("test".toValue())
        }
    }
}
