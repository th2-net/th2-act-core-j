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
package com.exactpro.th2.act.core.managers

import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.schema.message.MessageListener
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

typealias MessageBatchListener = MessageListener<MessageBatch>

private val LOGGER = KotlinLogging.logger {}

class SubscriptionManager: MessageListener<MessageBatch>, ISubscriptionManager {

    private val callbacks: Map<Direction, MutableList<MessageBatchListener>> = EnumMap(
        mapOf(Direction.FIRST to CopyOnWriteArrayList(), Direction.SECOND to CopyOnWriteArrayList())
    )

    override fun register(direction: Direction, listener: MessageBatchListener) {
        getMessageListeners(direction).add(listener)
    }

    override fun unregister(direction: Direction, listener: MessageBatchListener): Boolean {
        return getMessageListeners(direction).remove(listener)
    }

    override fun handler(consumerTag: String, messageBatch: MessageBatch) {
        if (messageBatch.messagesCount <= 0) {
            LOGGER.warn { "Empty batch received ${TextFormat.shortDebugString(messageBatch)}" }
            return
        }

        val direction = messageBatch.messagesList.first().metadata.id.direction
        val listeners: List<MessageBatchListener>? = callbacks[direction]

        if (listeners == null) {
            LOGGER.warn { "Unsupported direction  $direction. Batch: ${TextFormat.shortDebugString(messageBatch)}" }
            return
        }

        listeners.forEach { listener ->
            try {
                listener.handler(consumerTag, messageBatch)
            } catch (e: Exception) {
                LOGGER.error(e) {
                    "Cannot handle batch from $direction. Batch: ${TextFormat.shortDebugString(messageBatch)}"
                }
            }
        }
    }

    /**
     * Returns the list of message listeners for the specified [Direction].
     *
     * @throws IllegalArgumentException If the direction is invalid.
     */
    private fun getMessageListeners(direction: Direction): MutableList<MessageBatchListener> {
        return callbacks[direction] ?: throw IllegalArgumentException("Unsupported direction $direction.")
    }
}
