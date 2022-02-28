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
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.act.core.requests.Request
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isNotBlank
import strikt.assertions.isTrue

internal class TestMessageTypeValidation {

    /* Test List for MessageTypeValidation:
     * 1) Should invalidate a non-matching message type. V
     * 2) Should not invalidate a matching message type. V
     * 3) Should return invalidation reason after a non-matching message type was validated. V
     */

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should invalidate a non-matching message type`(matchingMessageType: IMessageType) {
        val validation = MessageTypeValidation(matchingMessageType)
        val invalidRequests = 10.randomMessageTypes().filter { it != matchingMessageType }.map {
            Request(it.toRandomMessage())
        }

        expect {
            invalidRequests.forEach { that(validation.on(it).isInvalid).isTrue() }
        }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should not invalidate a matching message type`(matchingMessageType: IMessageType) {
        val validation = MessageTypeValidation(matchingMessageType)
        val validRequests = 10 of { Request(matchingMessageType.toRandomMessage()) }

        expect {
            validRequests.forEach { that(validation.on(it).isInvalid).isFalse() }
        }
    }

    @Test
    fun `test should return invalidation reason after receiving a non-matching message type`() {
        val validation = MessageTypeValidation(TestMessageType.NEW_ORDER_SINGLE)

        val invalidRequest = Request(TestMessageType.REJECT.toRandomMessage())

        expect {
            that(validation.on(invalidRequest).invalidationReason).isNotBlank()
        }
    }

    @Test
    fun `test should not invalidate valid request after processing invalid ones`() {
        val validation = MessageTypeValidation(TestMessageType.REJECT)
        val requests = 10 of { Request(randomMessage()) }

        val validRequest = Request(TestMessageType.REJECT.toRandomMessage())

        requests.forEach { validation.on(it) }

        expect {
            that(validation.on(validRequest).isInvalid).isFalse()
        }
    }
}
