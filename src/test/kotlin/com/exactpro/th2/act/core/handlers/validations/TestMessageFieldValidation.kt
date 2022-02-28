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

package com.exactpro.th2.act.core.handlers.validations

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.messages.toMessage
import com.exactpro.th2.act.core.requests.Request
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isNotBlank
import strikt.assertions.isTrue

internal class TestMessageFieldValidation {

    /* Test List for MessageFieldValidation:
     * 1) Should invalidate a request containing a message with at least one missing field. V
     * 2) Should not invalidate a request containing a message with all fields present. V
     * 3) Should return invalidation reason after being passed an invalid request. V
     */

    @Test
    fun `test should invalidate a request containing a message with missing fields`() {
        val validation = MessageFieldValidation(TestField.CLIENT_ORDER_ID, TestField.PARTY_ID_SOURCE)

        val request = Request(
            mapOf(
                TestField.PRICE to randomString(),
                TestField.ORDER_QTY to randomString(),
                TestField.NO_PARTY_IDS to listOf(
                    mapOf(TestField.PARTY_ID_SOURCE to randomString())
                )
            ).toMessage()
        )

        expect {
            that(validation.on(request).isInvalid).isTrue()
        }
    }

    @Test
    fun `test should invalidate a request containing a message with blank value for expected fields`() {
        val validation = MessageFieldValidation(TestField.CLIENT_ORDER_ID, TestField.PARTY_ID_SOURCE)

        val request = Request(
            mapOf(
                TestField.CLIENT_ORDER_ID to randomString(),
                TestField.PRICE to randomString(),
                TestField.ORDER_QTY to randomString(),
                TestField.NO_PARTY_IDS to listOf(
                    mapOf(TestField.PARTY_ID_SOURCE to "")
                )
            ).toMessage()
        )

        expect {
            that(validation.on(request).isInvalid).isTrue()
        }
    }

    @Test
    fun `test should not invalidate a request containing a message with all fields present`() {
        val expectedFields = listOf(TestField.CLIENT_ORDER_ID, TestField.PARTY_ID_SOURCE)
        val validation = MessageFieldValidation(*expectedFields.toTypedArray())

        val request = Request(
            mapOf(
                TestField.PRICE to randomString(),
                TestField.ORDER_QTY to randomString(),
                TestField.NO_PARTY_IDS to listOf(
                    mapOf(TestField.PARTY_ID_SOURCE to randomString()),
                    mapOf(TestField.CLIENT_ORDER_ID to randomString())
                )
            ).toMessage()
        )

        expect {
            that(validation.on(request).isInvalid).describedAs(
                        """
                        Message: ${request.requestMessage}
                        Contains Fields: $expectedFields
                        """
                    ).isFalse()
        }
    }

    @Test
    fun `test should return invalidation reason after receiving an invalid request`() {
        val validation = MessageFieldValidation(TestField.TIME_IN_FORCE)

        val invalidRequest = Request(mapOf(TestField.CLIENT_ORDER_ID to randomString()).toMessage())

        expect {
            that(validation.on(invalidRequest).invalidationReason).isNotBlank()
        }
    }

    @Test
    fun `test should not invalidate valid request after processing invalid ones`() {
        val validation = MessageFieldValidation(TestField.CLIENT_ORDER_ID)
        val requests = 10 of { Request(randomMessage()) }

        val validRequest = Request(
            10.randomFields().plus(TestField.CLIENT_ORDER_ID to randomString()).toMap().toMessage()
        )

        requests.forEach { validation.on(it) }

        expect {
            that(validation.on(validRequest).isInvalid).isFalse()
        }
    }
}
