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
import com.exactpro.th2.act.core.rules.ICheckRuleFactory
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.schema.message.MessageListener
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import java.util.*

private val LOGGER = KotlinLogging.logger {}

class BiDirectionalMessageReceiver(
    private val subscriptionManager: ISubscriptionManager,
    monitor: IMessageResponseMonitor,
    private val outgoingRule: ICheckRule,
    private val incomingRuleSupplier: ICheckRuleFactory
): AbstractMessageReceiver(monitor) {

    private enum class State { START, OUTGOING_MATCHED, INCOMING_MATCHED }

    private var incomingRule: ICheckRule? = null
    private val incomingBuffer: Queue<MessageBatch> = LinkedList()

    private val incomingListener = MessageListener { _, messageBatch: MessageBatch ->
        processIncomingMessages(messageBatch)
    }

    private val outgoingListener = MessageListener { _, messageBatch: MessageBatch ->
        processOutgoingMessages(messageBatch)
    }

    @Volatile
    private var state = State.START

    init {
        subscriptionManager.register(Direction.FIRST, incomingListener)
        subscriptionManager.register(Direction.SECOND, outgoingListener)
    }

    override fun getResponseMessages(): List<Message> {
        return if (state == State.INCOMING_MATCHED) listOf(incomingRule!!.response!!) else emptyList()
    }

    override fun getProcessedMessageIDs(): Collection<MessageID> {
        return outgoingRule.processedIDs().plus(incomingRule?.processedIDs() ?: emptyList())
    }

    override fun close() {
        subscriptionManager.unregister(Direction.FIRST, incomingListener)
        subscriptionManager.unregister(Direction.SECOND, outgoingListener)
    }

    private fun processOutgoingMessages(batch: MessageBatch) {
        if (state == State.OUTGOING_MATCHED || state == State.INCOMING_MATCHED) { // Already found the outgoing message.
            return
        }

        if (anyMatches(batch, outgoingRule)) {

            val response = outgoingRule.response ?: throw IllegalStateException(
                "A matching was found in the batch but the response message is null."
            )

            LOGGER.debug { "Found match for outgoing rule. Match: ${TextFormat.shortDebugString(response)}" }

            incomingRule = incomingRuleSupplier.from(response)
            state = State.OUTGOING_MATCHED

            if (anyMatchesInBuffer(incomingRule!!)) {
                matchFound()
            }
        }
    }

    private fun processIncomingMessages(batch: MessageBatch) {
        if (state == State.INCOMING_MATCHED) { // Already found the response.
            return
        }

        if (state == State.START) {

            LOGGER.trace { "Buffering message batch: ${TextFormat.shortDebugString(batch)}" }
            synchronized(incomingBuffer) { incomingBuffer.add(batch) }
            return
        }

        if (anyMatchesInBuffer(incomingRule!!) || anyMatches(batch, incomingRule!!)) {
            matchFound()
        }
    }

    private fun anyMatchesInBuffer(incomingRule: ICheckRule): Boolean {
        synchronized(incomingBuffer) {
            return if (incomingBuffer.any { anyMatches(it, incomingRule) }) {
                true
            } else {
                incomingBuffer.clear()
                false
            }
        }
    }

    private fun anyMatches(batch: MessageBatch, rule: ICheckRule): Boolean {
        return batch.messagesList.any { rule.onMessage(it) }
    }

    private fun matchFound() {
        state = State.INCOMING_MATCHED
        notifyResponseMonitor()
    }
}
