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

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.toJson
import mu.KotlinLogging
import java.util.Queue
import java.util.ArrayDeque
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
class MessageBuffer(private val bufferSize: Int = 1000) {
    private val messageBuffer: Queue<Message> = ArrayDeque(bufferSize)

    fun getMessages(): Collection<Message> = messageBuffer

    fun add(msg: Message) {
        if (messageBuffer.size >= bufferSize) {
            val removed = messageBuffer.poll()
            LOGGER.trace { "Removed from buffer because the max size $bufferSize is reached: ${removed.toJson()}" }
        }
        messageBuffer.add(msg)
    }

    fun removeAll() = messageBuffer.clear()

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}