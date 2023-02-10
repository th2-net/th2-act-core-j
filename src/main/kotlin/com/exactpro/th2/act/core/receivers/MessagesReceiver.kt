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

package com.exactpro.th2.act.core.receivers

import com.exactpro.th2.act.core.messages.MessageBuffer
import com.exactpro.th2.act.core.managers.ISubscriptionManager
import com.exactpro.th2.act.core.monitors.WaitingTasksBuffer
import com.exactpro.th2.act.core.rules.PreFilterRule
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.schema.message.DeliveryMetadata
import com.exactpro.th2.common.schema.message.MessageListener
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

private val LOGGER = KotlinLogging.logger {}

@ThreadSafe
class MessagesReceiver(
    private val subscriptionManager: ISubscriptionManager,
    private val preFilterRule: PreFilterRule,
    bufferSize: Int = 1000
) : AutoCloseable {
    private val messageListener = createMessageListener()

    private val bufferLock = ReentrantReadWriteLock()
    @GuardedBy("bufferLock")
    private val messageBuffer = MessageBuffer(bufferSize)

    private val tasksLock = ReentrantLock()
    @GuardedBy("tasksLock")
    private val taskBuffer: Queue<WaitingTasksBuffer> = LinkedList()

    init {
        this.subscriptionManager.register(Direction.FIRST, messageListener)
        this.subscriptionManager.register(Direction.SECOND, messageListener)
    }

    private fun createMessageListener(): MessageListener<MessageBatch> =
        MessageListener { metadata: DeliveryMetadata, batch: MessageBatch ->
            LOGGER.debug("Received message batch of size ${batch.messagesCount}. Delivery metadata: $metadata")
            for (message in batch.messagesList) {
                if (preFilterRule.onMessage(message)) {

                    addToBuffer(message)

                    tasksLock.withLock {
                        val iterator = taskBuffer.iterator()
                        while (iterator.hasNext()) {
                            val task: WaitingTasksBuffer = iterator.next()
                            if (checkMessage(task, message)) {
                                iterator.remove()
                            }
                        }
                    }
                }
            }
        }

    private fun addToBuffer(message: Message) {
        bufferLock.write {
            messageBuffer.add(message)
        }
    }

    override fun close() {
        subscriptionManager.unregister(Direction.FIRST, messageListener)
        subscriptionManager.unregister(Direction.SECOND, messageListener)
    }


    private fun checkMessage(task: WaitingTasksBuffer, message: Message): Boolean =
        task.matchMessage(message) || task.isNotified


    private fun bufferSearch(task: WaitingTasksBuffer): Boolean {
        bufferLock.read {
            messageBuffer.getMessages().forEach {
                if (checkMessage(task, it)) {
                    return true
                }
            }
        }
        return false
    }

    fun submitTask(task: WaitingTasksBuffer) {
        if (bufferSearch(task)) {
            LOGGER.info { "Response found in buffer" }
            return
        }
        tasksLock.withLock {
            taskBuffer.add(task)
        }
    }

    fun cleanMessageBuffer(){
        bufferLock.write {
            messageBuffer.removeAll()
        }
    }
}