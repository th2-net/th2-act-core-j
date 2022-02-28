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

package com.exactpro.th2.act.core.routers

import com.exactpro.th2.act.*
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.message.messageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.io.IOException

internal class TestMessageRouter {

    /* Test List for MessageRouter:
     * 1) Should submit message to message batch router. V
     * 2) Should add connection ID to the message metadata before submitting if specified. V
     * 3) Should add parent ID to the message metadata before submitting if specified. V
     * 4) Should throw a RuntimeException if an error occurs while submitting the event. V
     */

    private lateinit var messageBatchRouter: StubMessageRouter<MessageBatch>
    private lateinit var messageRouter: MessageRouter

    @BeforeEach
    internal fun setUp() {
        messageBatchRouter = StubMessageRouter()
        messageRouter = MessageRouter(messageBatchRouter)
    }

    @Test
    fun `test should submit message to message batch router`() {
        val message = randomMessageType().toMessage(randomConnectionID())
        messageRouter.sendMessage(message)

        expect {
            that(messageBatchRouter.sent.messagesList).containsExactly(message)
        }
    }

    @Test
    fun `test should submit message to message batch router with the specified connection ID`() {
        val connectionID = randomConnectionID()
        val message = randomMessage()
        messageRouter.sendMessage(message, connectionID)

        expect {
            that(messageBatchRouter.sent.messagesList.size).isEqualTo(1)
            that(messageBatchRouter.sent.messagesList.first()).apply {
                get { metadata.id.connectionId }.isEqualTo(connectionID)
                get { metadata.messageType }.isEqualTo(message.messageType)
                get { fieldsMap }.isEqualTo(message.fieldsMap)
            }
        }
    }

    @Test
    fun `test should submit message to message batch router with the specified parent ID`() {
        val parentEventID = randomString().toEventID()
        val message = randomMessage()
        messageRouter.sendMessage(message, parentEventID = parentEventID)

        expect {
            that(messageBatchRouter.sent.messagesList.size).isEqualTo(1)
            that(messageBatchRouter.sent.messagesList.first()).apply {
                get { parentEventId }.isEqualTo(parentEventID)
                get { metadata }.isEqualTo(message.metadata)
                get { fieldsMap }.isEqualTo(message.fieldsMap)
            }
        }
    }

    @Test
    fun `test should submit message to message batch router with the specified connection and parent ID`() {
        val connectionID = randomConnectionID()
        val parentEventID = randomString().toEventID()
        val message = randomMessage()
        messageRouter.sendMessage(message, connectionID = connectionID, parentEventID = parentEventID)

        expect {
            that(messageBatchRouter.sent.messagesList.size).isEqualTo(1)
            that(messageBatchRouter.sent.messagesList.first()).apply {
                get { parentEventId }.isEqualTo(parentEventID)
                get { metadata.id.connectionId }.isEqualTo(connectionID)
                get { metadata.messageType }.isEqualTo(message.messageType)
                get { fieldsMap }.isEqualTo(message.fieldsMap)
            }
        }
    }

    @Test
    fun `test should throw exception when an error occurs while submitting the message`() {
        messageBatchRouter.throws { IOException("Some Problem Here") }

        expectThrows<MessageSubmissionException> {
            messageRouter.sendMessage(randomMessage())
        }
    }
}
