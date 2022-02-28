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

package com.exactpro.th2.act.core.rules

import com.exactpro.th2.act.core.receivers.IMessageReceiver
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageID

/**
 * An instance of [ICheckRule] which matches any message, but returns a null response. This
 * is used to signal complex [IMessageReceiver]s that they should not look for other response messages.
 */
object EmptyRule : ICheckRule {

    override fun onMessage(message: Message): Boolean = true

    override fun processedIDs(): Collection<MessageID> = emptyList()

    override fun getResponse(): Message? = null
}
