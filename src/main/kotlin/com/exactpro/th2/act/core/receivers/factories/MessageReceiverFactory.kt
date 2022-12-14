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
import com.exactpro.th2.act.core.receivers.MessagesReceiver
import com.exactpro.th2.act.core.rules.PreFilterRule
import com.exactpro.th2.common.grpc.Message

class MessageReceiverFactory(
    private val subscriptionManager: ISubscriptionManager,
    private val preFilter: (Message) -> Boolean,
    private val messageBufferSize: Int
) {
    fun create(): MessagesReceiver {
        return MessagesReceiver(
            subscriptionManager,
            PreFilterRule(preFilter),
            messageBufferSize
        )
    }
}