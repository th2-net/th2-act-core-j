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

package com.exactpro.th2.act.core.rules.filter

import com.exactpro.th2.common.event.Event.Status
import com.exactpro.th2.common.grpc.Message

class FilterReceiveBuilder(private val message: Message): IReceiveBuilder {
    var status: Status? = null
        get() = field
        private set(value) {
            field = value
        }

    override fun passOn(msgType: String, filter: Message.() -> Boolean): FilterReceiveBuilder {
        status = if (message.metadata.messageType == msgType && filter.invoke(message))
            Status.PASSED
        else null
        return this@FilterReceiveBuilder
    }

    override fun failOn(msgType: String, filter: Message.() -> Boolean): FilterReceiveBuilder {
        if (message.metadata.messageType == msgType && filter.invoke(message))
            status = Status.FAILED
        return this@FilterReceiveBuilder
    }
}