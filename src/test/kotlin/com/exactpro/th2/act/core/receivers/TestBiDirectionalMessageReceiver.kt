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

import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.rules.AbstractSingleConnectionRule
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.act.core.rules.ICheckRuleFactory
import com.exactpro.th2.act.randomConnectionID
import com.exactpro.th2.act.randomMessage
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.toBatch
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isSameInstanceAs

internal class TestBiDirectionalMessageReceiver {

    val connectionID = randomConnectionID()
    val subscriptionManager = SubscriptionManager()
    val monitor: IMessageResponseMonitor = mockk { justRun { responseReceived() } }

    fun receiver(outgoing: ICheckRule, incomingSupplier: (Message) -> ICheckRule): AbstractMessageReceiver =
        BiDirectionalMessageReceiver(
            subscriptionManager = subscriptionManager,
            monitor = monitor,
            outgoingRule = outgoing,
            incomingRuleSupplier = ICheckRuleFactory(incomingSupplier)
        )

    @Test
    fun `test should capture response message`() {
        val messageA = randomMessage(connectionID, direction = Direction.SECOND)
        val messageB = randomMessage(connectionID, direction = Direction.FIRST)

        val receiver = receiver(IdentityRule(messageA, connectionID)) { IdentityRule(messageB, connectionID) }
        receiver.use {
            subscriptionManager.handler(randomString(), messageA.toBatch())
            subscriptionManager.handler(randomString(), messageB.toBatch())
        }

        expect {
            that(receiver).apply {
                get { responseMessages.first() }.isSameInstanceAs(messageB)
                get { processedMessageIDs }.containsExactlyInAnyOrder(messageA.metadata.id, messageB.metadata.id)
            }
        }
        verify { monitor.responseReceived() }
    }

    @Test
    fun `test should capture response if incoming message is processed before outgoing`() {
        val messageA = randomMessage(connectionID, direction = Direction.SECOND)
        val messageB = randomMessage(connectionID, direction = Direction.FIRST)

        val receiver = receiver(IdentityRule(messageA, connectionID)) { IdentityRule(messageB, connectionID) }
        receiver.use {
            subscriptionManager.handler(randomString(), messageB.toBatch())
            subscriptionManager.handler(randomString(), messageA.toBatch())
        }

        expect {
            that(receiver).apply {
                get { responseMessages.first() }.isSameInstanceAs(messageB)
                get { processedMessageIDs }.containsExactlyInAnyOrder(messageA.metadata.id, messageB.metadata.id)
            }
        }
        verify { monitor.responseReceived() }
    }

    @Test
    fun `test should capture response if incoming message was received after a buffered message`() {
        val messageA = randomMessage(connectionID, direction = Direction.SECOND)
        val messageB = randomMessage(connectionID, direction = Direction.FIRST)
        val messageC = randomMessage(connectionID, direction = Direction.FIRST)

        val receiver = receiver(IdentityRule(messageA, connectionID)) { IdentityRule(messageC, connectionID) }
        receiver.use {
            subscriptionManager.handler(randomString(), messageB.toBatch())
            subscriptionManager.handler(randomString(), messageA.toBatch())
            subscriptionManager.handler(randomString(), messageC.toBatch())
        }

        expect {
            that(receiver).apply {
                get { responseMessages.first() }.isSameInstanceAs(messageC)
                get { processedMessageIDs }.containsExactlyInAnyOrder(
                    messageA.metadata.id, messageB.metadata.id, messageC.metadata.id
                )
            }
        }
        verify { monitor.responseReceived() }
    }

    @Test
    fun `test should return an empty list of response messages when no rules are matched`() {
        val messageA = randomMessage(connectionID, direction = Direction.SECOND)
        val messageB = randomMessage(connectionID, direction = Direction.FIRST)

        val receiver = receiver(IdentityRule(messageA, connectionID)) { IdentityRule(messageB, connectionID) }
        receiver.use { }

        expect {
            that(receiver).apply {
                get { responseMessages }.isEmpty()
                get { processedMessageIDs }.isEmpty()
            }
        }
        verify(exactly = 0) { monitor.responseReceived() }
    }

    @Test
    fun `test should return an empty list of response messages when only first rule is matched`() {
        val messageA = randomMessage(connectionID, direction = Direction.SECOND)
        val messageB = randomMessage(connectionID, direction = Direction.FIRST)
        val messageC = randomMessage(connectionID, direction = Direction.FIRST)

        val receiver = receiver(IdentityRule(messageA, connectionID)) { IdentityRule(messageB, connectionID) }
        receiver.use {
            subscriptionManager.handler(randomString(), messageA.toBatch())
            subscriptionManager.handler(randomString(), messageC.toBatch())
        }

        expect {
            that(receiver).apply {
                get { responseMessages }.isEmpty()
                get { processedMessageIDs }.containsExactlyInAnyOrder(messageA.metadata.id, messageC.metadata.id)
            }
        }
        verify(exactly = 0) { monitor.responseReceived() }
    }

    class IdentityRule(
        val message: Message, connectionId: ConnectionID
    ): AbstractSingleConnectionRule(connectionId) {

        override fun checkMessageFromConnection(message: Message): Boolean = (this.message === message)
    }
}
