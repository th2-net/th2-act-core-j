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
import com.exactpro.th2.act.core.receivers.MessageReceiver
import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message

/**
 * Creates a simple [IMessageReceiverFactory] that will match a single message, based on expected values for the
 * message fields.
 *
 * @param subscriptionManager The subscription manager for registering message listeners as a [ISubscriptionManager].
 * @param recipientConnection The connection that is expected to receive a response from the system.
 * @param requestMessage The request message sent to the system as a [Message].
 */
abstract class AbstractMessageReceiverFactory(
    private val subscriptionManager: ISubscriptionManager,
    private val recipientConnection: ConnectionID,
    private val requestMessage: Message
): IMessageReceiverFactory {

    override fun from(monitor: IMessageResponseMonitor): IMessageReceiver {

        val messageCheckRule = createMessageCheckRule(recipientConnection, requestMessage)
        return MessageReceiver(subscriptionManager, monitor, messageCheckRule, Direction.FIRST)
    }

    /**
     * Creates the [ICheckRule] that match the expected response message from the system.
     *
     * @param recipientConnection The connection to which the response message will be sent as [ConnectionID].
     * @param requestMessage The message that was sent to the system as a [Message].
     */
    protected abstract fun createMessageCheckRule(
        recipientConnection: ConnectionID, requestMessage: Message
    ): ICheckRule
}
