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

import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.randomMessage
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.toConnectionID
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.contains
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class TestSessionAliasValidation {

    /* Test List for SessionAlias:
     * 1) Should invalidate a message with a non-existent session alias. V
     * 2) Should not invalidate a message with a valid session alias. V
     * 3) Should return invalidation reason after a non-matching message was validated. V
     */

    @Test
    fun `test should not invalidate message`() {
        val validation = SessionAliasValidation(validSessionAliases = listOf("Alias 1", "Alias 2", "Alias 3"))
        val request = Request(randomMessage("Alias 1".toConnectionID()))

        expect {
            that(validation.on(request).isInvalid).isFalse()
        }
    }

    @Test
    fun `test should invalidate message`() {
        val validation = SessionAliasValidation(validSessionAliases = listOf("Alias 1", "Alias 2", "Alias 3"))
        val request = Request(randomMessage("Different alias 1".toConnectionID()))

        val result = validation.on(request)

        expect {
            that(result.isInvalid).isTrue()
            that(result.invalidationReason).contains("Different alias 1")
        }
    }

    @Test
    fun `test should not invalidate valid request after processing invalid ones`() {
        val validation = SessionAliasValidation(validSessionAliases = listOf("Alias 1", "Alias 2", "Alias 3"))

        repeat(10) {
            validation.on(Request(randomMessage("Different alias ${randomString()}".toConnectionID())))
        }

        expect {
            that(validation.on(Request(randomMessage("Alias 1".toConnectionID()))).isInvalid).isFalse()
        }
    }
}
