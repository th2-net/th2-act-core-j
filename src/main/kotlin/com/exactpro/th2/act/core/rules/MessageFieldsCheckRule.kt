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

import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Message
import com.google.protobuf.TextFormat
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class MessageFieldsCheckRule(
    requestConnId: ConnectionID, messageFields: List<MessageFields>
): AbstractSingleConnectionRule(requestConnId) {

    private val typeToMessageFields: Map<String, MessageFields>

    init {
        if (hasDuplicates(messageFields.map { fieldRule -> fieldRule.messageType })) {
            throw RuntimeException(
                "More than one field rule is specified for the same message type.\n" +
                "Specified Field Rules:\n$messageFields."
            )
        }

        typeToMessageFields = messageFields.associateBy { fieldRule -> fieldRule.messageType }
    }

    override fun checkMessageFromConnection(message: Message): Boolean {

        val messageType = message.metadata.messageType
        val expectedMessageFields = typeToMessageFields[messageType]

        return if (expectedMessageFields == null) {
            LOGGER.debug { "No field rule specified for message of type $messageType." }
            false
        } else {

            LOGGER.debug { "Checking the message: ${TextFormat.shortDebugString(message)}" }
            LOGGER.debug { "Field rule for message: $expectedMessageFields" }

            return if (expectedMessageFields.match(message)) {
                LOGGER.debug { "FieldCheckRule passed on ${expectedMessageFields.messageType} message." }
                true
            } else {
                LOGGER.debug { "Message doesn't match." }
                false
            }
        }
    }

    /**
     * Returns `true` if the specified list of [String]s contains a duplicate.
     */
    private fun hasDuplicates(values: List<String>): Boolean = values.size != values.toSet().size

    constructor(requestConnId: ConnectionID, vararg messageFields: MessageFields):
            this(requestConnId, messageFields.asList())
}
