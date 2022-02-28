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

import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.schema.message.MessageRouter
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import java.io.IOException

private val LOGGER = KotlinLogging.logger {}

class MessageRouter(private val messageBatchRouter: MessageRouter<MessageBatch>) {

    /**
     * Sends the specified message to the underlying message batch router.
     *
     * @param message The message to be sent as a [Message] object.
     * @param connectionID An optional connection ID that can be added to the message.
     * @param parentEventID An optional parent event ID that can be added to the message.
     *
     * @throws MessageSubmissionException if an error occurs while submitting the message.
     */
    fun sendMessage(message: Message, connectionID: ConnectionID? = null, parentEventID: EventID? = null) {

        try {
            LOGGER.debug("Message sending started.")

            val preparedMessage = Message.newBuilder(message).also { builder ->
                connectionID?.let { builder.setConnectionID(it) }
                parentEventID?.let { builder.setParentEventId(it) }
            }.build()

            messageBatchRouter.send(preparedMessage.toMessageBatch())
            LOGGER.debug { "Sent message to message router: ${TextFormat.shortDebugString(preparedMessage)}" }

        } catch (e: IOException) {
            LOGGER.error(e) { "Could not send message to message queue" }
            throw MessageSubmissionException("Could not send message to message queue", e)
        }
    }

    /**
     * Updates the connectionID in the [MessageMetadata] for this [Message.Builder] to the specified value.
     */
    private fun Message.Builder.setConnectionID(connectionID: ConnectionID): Message.Builder {

        val messageID = MessageID.newBuilder(this.metadata.id).setConnectionId(connectionID).build()
        val metadata = MessageMetadata.newBuilder(this.metadata).setId(messageID).build()

        return this.setMetadata(metadata)
    }

    /**
     * Creates a [MessageBatch] containing only this message.
     */
    private fun Message.toMessageBatch(): MessageBatch = MessageBatch.newBuilder().addMessages(this).build()
}
