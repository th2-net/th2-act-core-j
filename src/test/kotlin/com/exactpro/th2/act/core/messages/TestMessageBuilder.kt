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
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageMetadata
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.value.toValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
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
        val message = MessageBuilder create {
            body {
                TestField.ORDER_QTY toValue 23000
                TestField.PRICE toValue 122.123
                "ClOrdID" toValue "Some ID"
            }
        }

        expect {
            that(message).isEqualTo(
                Message.newBuilder().putAllFields(
                    mapOf(
                        TestField.ORDER_QTY.fieldName to 23000.toValue(),
                        TestField.PRICE.fieldName to 122.123.toValue(),
                        "ClOrdID" to "Some ID".toValue()
                    )
                ).build()
            )
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should add correct message type to the message metadata`(messageType: IMessageType) {
        val messageFromType = MessageBuilder create {
            metadata {
                withMessageType(messageType)
            }
            body {
                repeat(10) {
                    randomField() toValue randomString()
                }
            }
        }

        val messageFromString = MessageBuilder create {
            metadata {
                withMessageType(messageType.typeName)
            }
            body {
                repeat(10) {
                    randomField() toValue randomString()
                }
            }
        }

        expect {
            that(messageFromType.messageType).isEqualTo(messageFromString.messageType).isEqualTo(messageType.typeName)
        }
    }

    @Test
    fun `test should add correct body and metadata`() {
        val message = MessageBuilder create {
            metadata {
                withMessageType("SomeType")
            }
            body {
                TestField.ORDER_QTY toValue 23000
                TestField.NO_PARTY_IDS toMap {
                    TestField.PRICE toValue 122.123
                    TestField.CLIENT_ORDER_ID toValue "Some ID"
                }
            }
        }

        val expectedMetadata = MessageMetadata.newBuilder().setMessageType("SomeType").build()
        val expectedMessage = Message.newBuilder().setMetadata(expectedMetadata).putAllFields(
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
            that(message).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should wrap a nested map of fields`() {
        val message = MessageBuilder create {
            body {
                TestField.ORDER_QTY toValue 23000
                TestField.NO_PARTY_IDS toMap {
                    TestField.PRICE toValue 122.123
                    TestField.CLIENT_ORDER_ID toValue "Some ID"
                }
                "NoPartyIDsAsString" toMap {
                    "Price" toValue 122.123
                    TestField.CLIENT_ORDER_ID toValue "Some ID"
                }
            }
        }

        val expectedMessage = Message.newBuilder().putAllFields(
            mapOf(
                TestField.ORDER_QTY.fieldName to 23000.toValue(),
                TestField.NO_PARTY_IDS.fieldName to Message.newBuilder().putAllFields(
                    mapOf(
                        TestField.PRICE.fieldName to 122.123.toValue(),
                        TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                    )
                ).build().toValue(),
                "NoPartyIDsAsString" to Message.newBuilder().putAllFields(
                    mapOf(
                        "Price" to 122.123.toValue(),
                        TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                    )
                ).build().toValue()
            )
        ).build()

        expect {
            that(message).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should wrap a nested list of field maps`() {
        val message = MessageBuilder create {
            body {
                TestField.ORDER_QTY toValue "23000"
                TestField.NO_PARTY_IDS toList {
                    add {
                        TestField.PRICE toValue 122.123
                        TestField.CLIENT_ORDER_ID toValue "Some ID"
                    }
                    add {
                        "RandomField" toValue 1.123
                    }
                    add ("Some Value Here")
                    add (112.23)
                }
                "TestField" toList {
                    add {
                        TestField.ORDER_QTY toValue 122.123
                    }
                }
            }
        }

        val expectedMessage = Message.newBuilder().putAllFields(
            mapOf(
                TestField.ORDER_QTY.fieldName to "23000".toValue(),
                TestField.NO_PARTY_IDS.fieldName to ListValue.newBuilder().addAllValues(listOf(
                    Message.newBuilder().putAllFields(
                        mapOf(
                            TestField.PRICE.fieldName to "122.123".toValue(),
                            TestField.CLIENT_ORDER_ID.fieldName to "Some ID".toValue()
                        )
                    ).build().toValue(),
                    Message.newBuilder().putAllFields(
                        mapOf(
                            "RandomField" to 1.123.toValue()
                        )
                    ).build().toValue(),
                    "Some Value Here".toValue(),
                    112.23.toValue()
                )).build().toValue(),
                "TestField" to ListValue.newBuilder().addValues(
                    Message.newBuilder().putAllFields(
                        mapOf(
                            TestField.ORDER_QTY.fieldName to "122.123".toValue()
                        )
                    ).build().toValue()
                ).build().toValue()
            )
        ).build()

        expect {
            that(message).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `test should wrap a nested list of a list of field maps`() {
        val message = MessageBuilder create {
            body {
                TestField.ORDER_QTY toValue "23000"
                TestField.TRADE_REPORT_ID toList {
                    add {
                        TestField.NO_PARTY_IDS toList {
                            add {
                                TestField.PRICE toValue "122.123"
                                TestField.CLIENT_ORDER_ID toValue "Some ID"
                            }
                        }
                    }
                }
            }
        }

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
            that(message).isEqualTo(expectedMessage)
        }
    }
}
