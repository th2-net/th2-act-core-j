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

import com.exactpro.th2.act.core.managers.ISubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.schema.message.MessageListener
import com.google.protobuf.TextFormat
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class MessageReceiver(
    private val subscriptionManager: ISubscriptionManager,
    monitor: IMessageResponseMonitor,
    private val checkRule: ICheckRule,
    private val direction: Direction
): AbstractMessageReceiver(monitor) {

    private val messageListener = createMessageListener()
    private var matchedMessage: Message? = null

    init {
        this.subscriptionManager.register(direction, messageListener)
    }

    private fun createMessageListener(): MessageListener<MessageBatch> {
        return MessageListener { tag: String, batch: MessageBatch ->

            LOGGER.debug("Received message batch of size ${batch.serializedSize}. Consumer Tag: $tag")

            for (message in batch.messagesList) {

                if (messageMatched()) {
                    LOGGER.debug("The match was already found. Skip batch checking")
                    break
                }

                if (checkRule.onMessage(message)) {

                    LOGGER.debug { "Found match ${TextFormat.shortDebugString(message)}. Skipping other messages" }

                    matchedMessage = message
                    notifyResponseMonitor()
                    break
                }
            }
        }
    }

    private fun messageMatched(): Boolean {
        return matchedMessage != null
    }

    override fun close() {
        subscriptionManager.unregister(direction, messageListener)
    }

    override fun getResponseMessages(): List<Message> = listOfNotNull(matchedMessage)

    override fun getProcessedMessageIDs(): Collection<MessageID> = checkRule.processedIDs()
}
