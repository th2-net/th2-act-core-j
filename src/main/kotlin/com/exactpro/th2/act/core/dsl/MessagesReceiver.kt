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
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.monitors.MessageResponseMonitor
import com.exactpro.th2.act.core.receivers.IMessageReceiver
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.schema.message.MessageListener
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class MessagesReceiver(
    private val subscriptionManager: ISubscriptionManager,
    private var monitor: IMessageResponseMonitor = MessageResponseMonitor(),
    private val checkRule: ICheckRule
): IMessageReceiver {

    private var messageListener = createMessageListener(checkRule)
    private var matchedMessages = mutableListOf<Message>()

    init {
        this.subscriptionManager.register(Direction.FIRST, messageListener)
        this.subscriptionManager.register(Direction.SECOND, messageListener)
    }

    fun reloadMonitor(monitor: IMessageResponseMonitor){
        this.monitor = monitor
    }

    private fun createMessageListener(checkRule: ICheckRule): MessageListener<MessageBatch> {
        return MessageListener { tag: String, batch: MessageBatch ->
            LOGGER.debug("Received message batch of size ${batch.serializedSize}. Consumer Tag: $tag")
            for (message in batch.messagesList) {
                if (checkRule.onMessage(message)) {
                    matchedMessages.add(message)
                }
            }
            if (matchedMessages.isNotEmpty()) monitor.responseReceived()
        }
    }

    override fun close() {
        subscriptionManager.unregister(Direction.FIRST, messageListener)
        subscriptionManager.unregister(Direction.SECOND, messageListener)
    }

    override fun getResponseMessages(): List<Message> = matchedMessages

    override fun getProcessedMessageIDs(): Collection<MessageID> = checkRule.processedIDs()
}