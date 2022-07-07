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

package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.managers.ISubscriptionManager
import com.exactpro.th2.act.core.receivers.IMessageReceiver
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.schema.message.MessageListener
import mu.KotlinLogging
import java.util.*

private val LOGGER = KotlinLogging.logger {}

class MessagesReceiver(
    private val subscriptionManager: ISubscriptionManager,
    private val preFilterRule: PreFilterRule,
    bufferSize: Int = 1000
): IMessageReceiver {
    private val messageListener = createMessageListener()
    private val messageBuffer = MessageBuffer(bufferSize)
    private val taskBuffer: Queue<WaitingTasksBuffer> = LinkedList()

    init {
        this.subscriptionManager.register(Direction.FIRST, messageListener)
        this.subscriptionManager.register(Direction.SECOND, messageListener)
    }

    private fun createMessageListener(): MessageListener<MessageBatch> =
        MessageListener { tag: String, batch: MessageBatch ->
            LOGGER.debug("Received message batch of size ${batch.serializedSize}. Consumer Tag: $tag")
            for (message in batch.messagesList) {
                if (preFilterRule.onMessage(message)) {
                    messageBuffer.add(message)

                    if (taskBuffer.isNotEmpty()) {
                        val task = taskBuffer.element()
                        if (!task.isNotified() && task.onMessage(message)) {
                            task.monitorResponseReceived()
                        }
                    }
                }
            }
        }

    override fun close() {
        subscriptionManager.unregister(Direction.FIRST, messageListener)
        subscriptionManager.unregister(Direction.SECOND, messageListener)
    }

    override fun getResponseMessages(): List<Message> = messageBuffer.getMessages()

    override fun getProcessedMessageIDs(): Collection<MessageID> = preFilterRule.processedIDs()

    fun incomingMessage(): List<Message> {
        val task = taskBuffer.element()
        if (task.isNotified()) {
            taskBuffer.remove()
            return listOf(task.incomingMessage())
        }
        return listOf()
    }

    fun bufferSearch(receiveRule: ReceiveRule): List<Message> {
        responseMessages.forEach {
            if (receiveRule.onMessage(it)) {
                return listOf(it)
            }
        }
        return listOf()
    }

    fun submitTask(task: WaitingTasksBuffer) {
        taskBuffer.add(task)
    }

    fun cleanMessageBuffer(){
        messageBuffer.removeAll()
    }
}