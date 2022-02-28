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

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.messages.IField
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.messageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class TestEchoCheckRule {

    /* Test List for EchoCheckRule:
     * 1) Should match messages that have the same fields and message type as the one specified. V
     * 2) Should not match messages with a different message type. V
     * 3) Should not match messages with different fields. V
     */

    private lateinit var connectionID: ConnectionID
    private lateinit var parentEventID: EventID
    private lateinit var messageType: IMessageType
    private lateinit var messageFields: List<Pair<IField, Any>>
    private lateinit var expectedMessage: Message


    @BeforeEach
    internal fun setUp() {
        connectionID = randomConnectionID()
        parentEventID = "Expected ID ${randomString()}".toEventID()
        messageType = randomMessageType()
        messageFields = 10.randomFields().distinctBy { it.first }.toList()

        expectedMessage = messageType.toMessage(
            connectionID = connectionID,
            fields = messageFields.toMap(),
            parentEventID = parentEventID
        )
    }

    @Test
    fun `test should match message`() {
        expect {
            that(EchoCheckRule(expectedMessage, parentEventID).onMessage(expectedMessage)).isTrue()
        }
    }

    @Test
    fun `test should match message with same parent event ID`() {
        val matchingMessages = 10 of {
            messageType.toMessage(
                connectionID = connectionID,
                fields = messageFields.shuffled().toMap(),
                direction = randomDirection(),
                parentEventID = parentEventID
            )
        }

        val rule = EchoCheckRule(expectedMessage, parentEventID)

        expect {
            matchingMessages.forEach {
                that(rule.onMessage(it)).describedAs {
                    if (this) "Messages match."
                    else {
                        """
                        Type: ${expectedMessage.messageType}, matches: ${it.messageType}
                        Fields: ${expectedMessage.fieldsMap}, match: ${it.fieldsMap}                  
                        """.trimStart()
                    }
                }.isTrue()
            }
        }
    }

    @Test
    fun `test should not match message with different parent ID`() {
        val nonMatchingMessages = 10.randomMessageTypes(messageType).map {
            it.toMessage(
                connectionID = connectionID,
                fields = messageFields.toMap(),
                direction = randomDirection(),
                parentEventID = "Unexpected ID ${randomString()}".toEventID()
            )
        }

        val rule = EchoCheckRule(expectedMessage, parentEventID)

        expect {
            nonMatchingMessages.forEach {
                that(rule.onMessage(it)).isFalse()
            }
        }
    }
}
