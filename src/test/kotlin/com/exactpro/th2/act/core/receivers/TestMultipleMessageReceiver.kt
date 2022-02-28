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

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.managers.MessageBatchListener
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.act.core.rules.ICheckRuleFactory
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder

internal class TestMultipleMessageReceiver {

    /* Test List for MultipleMessageReceiver:
     * 1) Should create MultipleMessageReceiver from monitor, subscription manager and just the first check rule. V
     * 2) Should create MultipleMessageReceiver with first check rule and follow-up rule suppliers. V
     * 3) Should match incoming message to first rule and notify message response monitor (no follow-up rules). V
     * 4) Should not notify message response monitor if the first message was not matched. V
     * 5) Should match incoming and follow-up messages and notify message response monitor. V
     * 6) Should not notify message response monitor if follow-up messages have not been matched. V
     * 7) Should match follow-up messages if they were received before the first message. V
     * 8) Should return correct response messages when all rules are matched. V
     * 9) Should return message IDs of all messages with a connection ID matching at least one of the rules. V
     * 10) Should unregister message listeners when closed. V
     */

    /**
     * Returns a common connection ID (used for convenient to omit the details of the ID).
     */
    private fun commonConnectionID(): ConnectionID = "Common Test Alias".toConnectionID()

    /**
     * Returns a [MultipleMessageReceiver] created with the specified [ICheckRule] and [ICheckRuleFactory]s.
     */
    private fun receiver(
        firstRule: ICheckRule, vararg followUpRuleFactory: ICheckRuleFactory
    ): MultipleMessageReceiver {
        return MultipleMessageReceiver(subscriptionManager, responseMonitor, firstRule, followUpRuleFactory.asList())
    }

    /**
     * Returns a [MultipleMessageReceiver] created with the specified [ICheckRule] and [ICheckRuleFactory] iterable.
     */
    private fun receiver(
        firstRule: ICheckRule, followUpRuleFactories: Iterable<ICheckRuleFactory>
    ): MultipleMessageReceiver {
        return receiver(firstRule, *followUpRuleFactories.toList().toTypedArray())
    }

    /**
     * Returns a [MultipleMessageReceiver] created with the specified [ICheckRule] and [ICheckRuleFactory] iterable.
     * This receiver will be created so that it returns all matched messages.
     */
    private fun returnsAllReceiver(
        firstRule: ICheckRule, followUpRuleFactories: Iterable<ICheckRuleFactory>
    ): MultipleMessageReceiver {
        return MultipleMessageReceiver(
            subscriptionManager = subscriptionManager,
            monitor = responseMonitor,
            firstRule = firstRule,
            followUpRuleFactories = followUpRuleFactories.toList(),
            returnFirstMatch = true
        )
    }

    private lateinit var responseMonitor: IMessageResponseMonitor
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeEach
    internal fun setUp() {
        responseMonitor = mockk { justRun { responseReceived() } }
        subscriptionManager = spyk { }
    }

    @Test
    fun `test should create receiver from monitor, subscription manager and first rule`() {
        val firstRule = randomConnectionID().toRandomMessageFieldsCheckRule()

        assertDoesNotThrow { receiver(firstRule) }
    }

    @Test
    fun `test should create receiver with follow-up rules`() {
        val firstRule = randomConnectionID().toRandomMessageFieldsCheckRule()
        val followUpRuleFactories = 10.of {
            ICheckRuleFactory { randomConnectionID().toRandomMessageFieldsCheckRule() }
        }.toList()

        assertDoesNotThrow { receiver(firstRule, followUpRuleFactories) }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should match message to first rule and notify message response monitor`(messageType: IMessageType) {
        val randomMessages = 5.of { randomMessage() }.plus(5.of { randomMessage(commonConnectionID()) })
        val expectedMessage = messageType.toRandomMessage(commonConnectionID(), direction = Direction.FIRST)

        val firstRule = commonConnectionID().toMessageFieldsCheckRule(messageType.toMessageFields())

        receiver(firstRule).use {
            randomMessages.toList().shuffled().plus(expectedMessage).forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        verify { responseMonitor.responseReceived() }
    }

    @ParameterizedTest
    @EnumSource(TestMessageType::class)
    fun `test should not notify message response monitor when first rule is not matched`(messageType: IMessageType) {
        val randomMessages = 10.randomMessageTypes()
                .filter { it != messageType }
                .map { it.toRandomMessage(commonConnectionID()) }

        val firstRule = commonConnectionID().toMessageFieldsCheckRule(messageType.toMessageFields())

        receiver(firstRule).use {
            randomMessages.forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        verify(exactly = 0) { responseMonitor.responseReceived() }
    }

    @Test
    fun `test should notify response monitor when first and follow-up rules are matched`() {
        val firstMessageType = randomMessageType()
        val followUpMessageType = randomMessageType()

        val firstMessage = firstMessageType.toRandomMessage(
            "Test Alias 1".toConnectionID(), direction = Direction.FIRST
        )

        val followUpMessage = followUpMessageType.toRandomMessage(
            "Test Alias 2".toConnectionID(), direction = Direction.FIRST
        )

        val firstRule = "Test Alias 1".toConnectionID().toMessageFieldsCheckRule(firstMessageType.toMessageFields())
        val followUpRuleFactory = ICheckRuleFactory {
            "Test Alias 2".toConnectionID().toMessageFieldsCheckRule(followUpMessageType.toMessageFields())
        }

        receiver(firstRule, followUpRuleFactory).use {
            subscriptionManager.handler(randomString(), firstMessage.toBatch())
            subscriptionManager.handler(randomString(), followUpMessage.toBatch())
        }

        verify { responseMonitor.responseReceived() }
    }

    @Test
    fun `test should not notify response monitor if follow-up rules are not matched`() {
        val firstMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage("Test Alias 1".toConnectionID())
        val followUpMessage = TestMessageType.EXECUTION_REPORT.toRandomMessage("Test Alias 2".toConnectionID())

        val firstRule =
            "Test Alias 1".toConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val followUpRuleFactories = listOf(ICheckRuleFactory {
            "Test Alias 2".toConnectionID().toMessageFieldsCheckRule(TestMessageType.EXECUTION_REPORT.toMessageFields())
        }, ICheckRuleFactory {
            "Test Alias 2".toConnectionID().toMessageFieldsCheckRule(TestMessageType.REJECT.toMessageFields())
        })

        receiver(firstRule, followUpRuleFactories).use {
            subscriptionManager.handler(randomString(), firstMessage.toBatch())
            subscriptionManager.handler(randomString(), followUpMessage.toBatch())
        }

        verify(exactly = 0) { responseMonitor.responseReceived() }
    }

    @Test
    fun `test should match follow-up messages if they were received before the first message`() {
        val firstMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage(
            "Test Alias 1".toConnectionID(), direction = Direction.FIRST
        )
        val followUpMessages = listOf(
            TestMessageType.EXECUTION_REPORT.toRandomMessage(
                "Test Alias 2".toConnectionID(), direction = Direction.FIRST
            ), TestMessageType.REJECT.toRandomMessage(
                "Test Alias 3".toConnectionID(), direction = Direction.FIRST
            )
        )

        val firstRule =
            "Test Alias 1".toConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val followUpRuleFactories = listOf(ICheckRuleFactory {
            "Test Alias 2".toConnectionID().toMessageFieldsCheckRule(TestMessageType.EXECUTION_REPORT.toMessageFields())
        }, ICheckRuleFactory {
            "Test Alias 3".toConnectionID().toMessageFieldsCheckRule(TestMessageType.REJECT.toMessageFields())
        })

        receiver(firstRule, followUpRuleFactories).use {
            followUpMessages.shuffled().forEach { subscriptionManager.handler(randomString(), it.toBatch()) }
            subscriptionManager.handler(randomString(), firstMessage.toBatch())
        }

        verify { responseMonitor.responseReceived() }
    }

    @Test
    fun `test should return correct response messages when all rules are matched`() {
        val firstMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage(
            "Test Alias 1".toConnectionID(), direction = Direction.FIRST
        )
        val followUpMessages = listOf(
            TestMessageType.EXECUTION_REPORT.toRandomMessage(
                "Test Alias 2".toConnectionID(), direction = Direction.FIRST
            ), TestMessageType.REJECT.toRandomMessage(
                "Test Alias 3".toConnectionID(), direction = Direction.FIRST
            )
        )

        val randomMessages = 10.randomMessageTypes().filter {
                    !setOf(
                        TestMessageType.NEW_ORDER_SINGLE,
                        TestMessageType.EXECUTION_REPORT,
                        TestMessageType.REJECT
                    ).contains(it)
                }.map { it.toRandomMessage(randomConnectionID()) }

        val firstRule =
            "Test Alias 1".toConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val followUpRuleFactories = listOf(ICheckRuleFactory {
            "Test Alias 2".toConnectionID().toMessageFieldsCheckRule(TestMessageType.EXECUTION_REPORT.toMessageFields())
        }, ICheckRuleFactory {
            "Test Alias 3".toConnectionID().toMessageFieldsCheckRule(TestMessageType.REJECT.toMessageFields())
        })

        val receiver = returnsAllReceiver(firstRule, followUpRuleFactories)

        receiver.use {
            randomMessages.plus(followUpMessages).plus(firstMessage).shuffled().forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        expect {
            that(receiver.responseMessages).containsExactlyInAnyOrder(followUpMessages.plus(firstMessage))
        }
    }


    @Test
    fun `test should return message IDs of all messages with connection ID matching at least one rule`() {
        val firstMessage = TestMessageType.NEW_ORDER_SINGLE.toRandomMessage(
            "Test Alias 1".toConnectionID(), direction = Direction.FIRST
        )

        val followUpMessages = listOf(
            TestMessageType.EXECUTION_REPORT.toRandomMessage(
                "Test Alias 2".toConnectionID(), direction = Direction.FIRST
            ),
            TestMessageType.REJECT.toRandomMessage(
                "Test Alias 3".toConnectionID(), direction = Direction.FIRST
            )
        )

        val randomMessages = 10.randomMessageTypes().filter {
                    !setOf(
                        TestMessageType.NEW_ORDER_SINGLE,
                        TestMessageType.EXECUTION_REPORT,
                        TestMessageType.REJECT
                    ).contains(it)
                }.map { it.toRandomMessage("Wrong Alias".toConnectionID()) }

        val firstRule =
            "Test Alias 1".toConnectionID().toMessageFieldsCheckRule(TestMessageType.NEW_ORDER_SINGLE.toMessageFields())

        val followUpRuleFactories = listOf(ICheckRuleFactory {
            "Test Alias 2".toConnectionID().toMessageFieldsCheckRule(TestMessageType.EXECUTION_REPORT.toMessageFields())
        }, ICheckRuleFactory {
            "Test Alias 3".toConnectionID().toMessageFieldsCheckRule(TestMessageType.REJECT.toMessageFields())
        })

        val receiver = returnsAllReceiver(firstRule, followUpRuleFactories)

        receiver.use {
            randomMessages.plus(followUpMessages).plus(firstMessage).shuffled().forEach {
                subscriptionManager.handler(randomString(), it.toBatch())
            }
        }

        expect {
            that(receiver.processedMessageIDs).containsExactlyInAnyOrder(
                followUpMessages.plus(firstMessage).map { it.metadata.id }
            )
        }
    }

    @Test
    fun `test should unregister message listeners when closed`() {
        val firstRule = randomConnectionID().toRandomMessageFieldsCheckRule()
        val followUpRuleFactories = 10 of {
            ICheckRuleFactory {
                randomConnectionID().toRandomMessageFieldsCheckRule()
            }
        }

        val registeredListeners = mutableListOf<MessageBatchListener>()
        every { subscriptionManager.register(Direction.FIRST, any()) } answers {
            registeredListeners.add(this.secondArg())
            callOriginal()
        }

        val unregisteredListeners = mutableListOf<MessageBatchListener>()
        every { subscriptionManager.unregister(Direction.FIRST, any()) } answers {
            unregisteredListeners.add(this.secondArg())
            callOriginal()
        }

        receiver(firstRule, *followUpRuleFactories).use { }

        expect {
            that(unregisteredListeners).containsExactlyInAnyOrder(registeredListeners)
        }
    }
}
