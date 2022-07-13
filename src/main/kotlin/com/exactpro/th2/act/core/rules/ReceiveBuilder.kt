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

import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.common.grpc.Message

enum class StatusReceiveBuilder(val value: Boolean) {
    PASSED(true),
    OTHER(false),
    FAILED(false)
}

class ReceiveBuilder(private val message: Message) {
    private val fail = StatusReceiveBuilder.FAILED
    private val other = StatusReceiveBuilder.OTHER
    private val pass = StatusReceiveBuilder.PASSED

    private var status: StatusReceiveBuilder = pass

    fun getStatus(): StatusReceiveBuilder = status

    private val msgTypes = HashSet<IMessageType>()

    fun messageTypes(): List<IMessageType> = msgTypes.toList()

    fun passOn(msgType: String, filter: Message.() -> Boolean): ReceiveBuilder {
        msgTypes.add(IMessageType { msgType })

        status = if (message.metadata.messageType == msgType && filter.invoke(message)) pass
        else other
        return this@ReceiveBuilder
    }

    fun failOn(msgType: String, filter: Message.() -> Boolean): ReceiveBuilder {
        msgTypes.add(IMessageType { msgType })

        if (message.metadata.messageType == msgType && filter.invoke(message)) {
            status = fail
        }
        return this@ReceiveBuilder
    }

    operator fun invoke(filter: ReceiveBuilder.() -> ReceiveBuilder) = filter()
}