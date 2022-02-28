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
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOGGER = KotlinLogging.logger {}

/**
 * Creates a MultipleMessageReceiver object that:
 * 1. Attempts to match the specified "first" rule.
 * 2. Based on the message matched to the first rule, attempts to match all followup rules.
 *
 * @param subscriptionManager The subscription manager for registering message listeners as a [ISubscriptionManager].
 * @param monitor A [IMessageResponseMonitor] object that will be notified once the response is received.
 * @param firstRule The [ICheckRule] to be used to match the first message.
 * @param followUpRuleFactories A [List] of followup rule factories as [ICheckRuleFactory] objects.
 * @param returnFirstMatch If `true` the message matching the first rule will also be returned.
 */
class MultipleMessageReceiver(
    private val subscriptionManager: ISubscriptionManager,
    private val monitor: IMessageResponseMonitor,
    private val firstRule: ICheckRule,
    private val followUpRuleFactories: Collection<ICheckRuleFactory> = emptyList(),
    private val returnFirstMatch: Boolean = true
): AbstractMessageReceiver(monitor) {

    private val firstRuleListener = createFirstMessageListener()
    private val followUpRuleListeners: Array<MessageListener<MessageBatch>?> =
        Array(followUpRuleFactories.size) { null }
    private val followUpRules: Array<ICheckRule?> = Array(followUpRuleFactories.size) { null }
    private val incomingBuffer: Queue<MessageBatch> = LinkedList()

    /**
     * Keeps track of the status of individual rules. If a rule has been matched the value
     * will be set to `true` for the respective index in this array.
     */
    private var errorOccurred: Boolean = false
    private var firstRuleMatched: Boolean = false
    private val followUpRuleMatched: BooleanArray = BooleanArray(followUpRuleFactories.size) { false }

    private val firstRuleLock: Lock = ReentrantLock()
    private val followUpRuleLocks: Array<Lock> = Array(followUpRuleFactories.size) { ReentrantLock() }
    private val bufferLock: Lock = ReentrantLock()  // NOTE: Added just in case, but may be redundant currently.

    init { registerListeners() }

    /**
     * @return A [List] of the matched response messages.
     */
    override fun getResponseMessages(): List<Message> {

        val responseMessages: MutableList<Message> = ArrayList()

        if (firstRuleMatched) {

            if (returnFirstMatch) {
                responseMessages.add(firstRule.response!!)
            }

            followUpRuleMatched.forEachIndexed { index, ruleMatched ->
                if (ruleMatched && followUpRules[index]!!.response != null) {
                    responseMessages.add(followUpRules[index]!!.response!!)
                }
            }
        }
        return responseMessages
    }

    /**
     * @return A [List] of all the processed MessageIDs.
     */
    override fun getProcessedMessageIDs(): Collection<MessageID> {
        return listOf(
            firstRule.processedIDs(),
            followUpRules.filterNotNull().flatMap { rule -> rule.processedIDs() }).flatten()
    }

    /**
     * Closes this Message Receiver object by unregistering all registered message listeners.
     */
    override fun close() {
        subscriptionManager.unregister(Direction.FIRST, firstRuleListener)

        followUpRuleListeners.filterNotNull().forEach { listener ->
            subscriptionManager.unregister(Direction.FIRST, listener)
        }
    }

    /**
     * Registers appropriate listeners for the first and followup rules.
     */
    private fun registerListeners() {

        subscriptionManager.register(Direction.FIRST, firstRuleListener)

        followUpRuleFactories.forEachIndexed { ruleIndex, checkRuleSupplier ->

            val followupRuleListener = createFollowupMessageListener(checkRuleSupplier, ruleIndex)

            followUpRuleListeners[ruleIndex] = followupRuleListener
            subscriptionManager.register(Direction.FIRST, followupRuleListener)
        }
    }

    /**
     * Creates a message listener for the first rule specified for this [MultipleMessageReceiver].
     */
    private fun createFirstMessageListener(): MessageListener<MessageBatch> =
        MessageListener listener@{ _, batch: MessageBatch -> processFirstMessage(batch) }

    /**
     * Processes the specified message batch to find a match for the first expected message.
     */
    private fun processFirstMessage(batch: MessageBatch) {
        firstRuleLock.withLock {
            if (firstRuleMatched || errorOccurred) {
                return
            }

            addToBuffer(batch)

            if (anyMatches(batch, firstRule)) {

                val response = firstRule.response

                if (response == null) {
                    errorOccurred = true
                    throw IllegalStateException("Rule has found a match but the response message is null.")
                }

                LOGGER.debug {
                    "Found match for the first rule. Match: ${TextFormat.shortDebugString(response)}"
                }

                firstRuleMatched = true
                checkBufferForFollowUpMessages()
                checkFollowUpRules()
            }
        }
    }

    /**
     * Creates a followup rule listener from the specified supplier.
     *
     * @param followupRuleFactory The function that will return the followup rule.
     * @param ruleIndex The index of the followup rule. Used to store various information about it's state.
     * @return A message listener for the rule created from the specified rule supplier.
     */
    private fun createFollowupMessageListener(
        followupRuleFactory: ICheckRuleFactory, ruleIndex: Int
    ): MessageListener<MessageBatch> = MessageListener listener@{ _, batch: MessageBatch ->
        processFollowUpMessage(followupRuleFactory, ruleIndex, batch)
    }

    /**
     * Processes the specified message batch according to the rule provided by the given [ICheckRuleFactory].
     */
    private fun processFollowUpMessage(followupRuleFactory: ICheckRuleFactory, ruleIndex: Int, batch: MessageBatch) {
        followUpRuleLocks[ruleIndex].withLock {

            if (errorOccurred) {
                return
            }

            if (firstRuleMatched) {
                if (followUpRuleMatched[ruleIndex]) {
                    return
                }

                val followupRule = getFollowupRule(followupRuleFactory, firstRule.response)
                followUpRules[ruleIndex] = followupRule

                if (followupRule == null) {
                    return
                }

                if (anyMatches(batch, followupRule)) {
                    followUpRuleMatched[ruleIndex] = true
                    checkFollowUpRules()
                }
            }
        }
    }

    /**
     * Adds the specified [MessageBatch] to the internal buffer.
     *
     * @param batch The [MessageBatch] to be added to the buffer.
     */
    private fun addToBuffer(batch: MessageBatch): Unit = bufferLock.withLock {
        LOGGER.trace { "Buffering message batch: ${TextFormat.shortDebugString(batch)}" }
        incomingBuffer.add(batch)
    }

    /**
     * Checks to see if any message in the message buffer matches the specified rule. The buffer will be cleared
     * after invoking this function.
     */
    private fun checkBufferForFollowUpMessages(): Unit = bufferLock.withLock {
        while(incomingBuffer.isNotEmpty()) {
            val bufferedBatch = incomingBuffer.poll()

            followUpRuleFactories.forEachIndexed { ruleIndex, ruleFactory ->
                processFollowUpMessage(ruleFactory, ruleIndex, bufferedBatch)
            }
        }
    }

    /**
     * Returns a followup rule from the specified rule supplier and response message or null
     * if a rule couldn't be created.
     *
     * @param followupRuleSupplier The function that will generate the followup rule.
     * @param response The response message to be passed to the rule supplier.
     * @return The [ICheckRule] created from the rule supplier or null.
     */
    private fun getFollowupRule(followupRuleSupplier: ICheckRuleFactory, response: Message?): ICheckRule? {
        return try {
            followupRuleSupplier.from(response)
        } catch (ex: Exception) {

            LOGGER.error(ex) { "Cannot create followup rule on response: ${TextFormat.shortDebugString(response)}" }
            errorOccurred = true
            null
        }
    }

    /**
     * Checks to see if any message from the message batch matches the specified rule.
     *
     * @param batch The [MessageBatch] to be checked.
     * @param rule The rule which the messages will be checked against.
     * @return `true` if a match is found, `false` otherwise.
     */
    private fun anyMatches(batch: MessageBatch, rule: ICheckRule): Boolean =
        batch.messagesList.any { message -> rule.onMessage(message) }

    /**
     * Checks if all follow-up rules have been matched, and notifies the [IMessageResponseMonitor] if so.
     */
    private fun checkFollowUpRules() {
        if (followUpRuleMatched.all { it }) {
            notifyResponseMonitor()
        }
    }
}
