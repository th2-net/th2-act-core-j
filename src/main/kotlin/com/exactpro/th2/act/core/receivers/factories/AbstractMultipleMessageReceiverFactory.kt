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

package com.exactpro.th2.act.core.receivers.factories

import com.exactpro.th2.act.core.managers.ISubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.core.receivers.IMessageReceiver
import com.exactpro.th2.act.core.receivers.IMessageReceiverFactory
import com.exactpro.th2.act.core.receivers.MultipleMessageReceiver
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.act.core.rules.ICheckRuleFactory
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Message

/**
 * Creates a complex [IMessageReceiverFactory] that will match multiple messages.
 *
 * The created receiver will first capture an initial response from the system, according to the specified [ICheckRule]
 * for the first message. All follow-up messages will be retrieved based on the [ICheckRule] created from the data of
 * the first message.
 *
 * @param subscriptionManager The subscription manager for registering message listeners as a [ISubscriptionManager].
 * @param recipientConnection The connection that is expected to receive the initial response from the system.
 * @param thirdRecipientConnections A [List] of connections that should receive follow-up responses from the system.
 * @param requestMessage The request message sent to the system as a [Message].
 * @param returnFirstMatch If `true`, the initial message will be included in the captured response.
 */
abstract class AbstractMultipleMessageReceiverFactory(
    private val subscriptionManager: ISubscriptionManager,
    private val recipientConnection: ConnectionID,
    private val thirdRecipientConnections: List<ConnectionID>,
    private val requestMessage: Message,
    private val returnFirstMatch: Boolean
): IMessageReceiverFactory {

    override fun from(monitor: IMessageResponseMonitor): IMessageReceiver {

        val firstCheckRule: ICheckRule = createFirstMessageCheckRule(recipientConnection, requestMessage)

        val followUpRuleSuppliers: List<ICheckRuleFactory> = thirdRecipientConnections.map { connectionID ->
            ICheckRuleFactory { responseMessage ->
                createFollowUpMessageCheckRule(connectionID, requestMessage, responseMessage)
            }
        }

        return MultipleMessageReceiver(
            subscriptionManager, monitor, firstCheckRule, followUpRuleSuppliers, returnFirstMatch
        )
    }

    /**
     * Creates the [ICheckRule] that match the expected response message from the system.
     *
     * @param recipientConnection The connection to which the response message will be sent as [ConnectionID].
     * @param requestMessage The message that was sent to the system as a [Message].
     */
    protected abstract fun createFirstMessageCheckRule(
        recipientConnection: ConnectionID, requestMessage: Message
    ): ICheckRule

    /**
     * Creates the [ICheckRule] that matches the expected follow-up message for the specified connection.
     *
     * @param recipientConnection The connection to which the response message will be sent as [ConnectionID].
     * @param requestMessage The message that was sent to the system as a [Message].
     * @param responseMessage The first message that was returned as a response from the system as a [Message].
     */
    protected abstract fun createFollowUpMessageCheckRule(
        recipientConnection: ConnectionID, requestMessage: Message, responseMessage: Message
    ): ICheckRule
}
