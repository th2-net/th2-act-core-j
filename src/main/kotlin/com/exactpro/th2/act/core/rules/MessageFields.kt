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

import com.exactpro.th2.act.core.messages.IField
import com.exactpro.th2.common.grpc.MessageOrBuilder
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class MessageFields(val messageType: String, private val expectedValues: Map<IField, String>) {

    /**
     * Checks whether the specified message matches this [MessageFields].
     *
     * @param message The message to be checked
     * @return `true` if the specified message is a match, `false` otherwise.
     */
    fun match(message: MessageOrBuilder): Boolean {

        if (message.metadata.messageType != messageType) {
            return false
        }

        for ((field, expectedValue) in expectedValues) {

            val messageValue = message.fieldsMap[field.fieldName]?.simpleValue

            if (messageValue == null) {
                LOGGER.debug("Field ${field.fieldName} not present in message.")
                return false

            } else if (messageValue != expectedValue) {
                LOGGER.debug(
                    "Value mismatch for field ${field.fieldName}.\n" +
                    "Expected: $expectedValue, Received: $messageValue."
                )
                return false
            }
        }
        return true
    }

    override fun toString(): String {
        return """
            MessageFields(
                Expected Message Type: $messageType
                Expected Fields: ${expectedValues.map { "${it.key}: ${it.value}" }.joinToString("\n")}
            )
        """.trimIndent()
    }
}
