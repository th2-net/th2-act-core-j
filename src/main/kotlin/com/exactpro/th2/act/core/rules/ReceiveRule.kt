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

import com.exactpro.th2.act.core.rules.filter.FilterReceiveBuilder
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.grpc.Message

class ReceiveRule(
    private val filterReceive: AbstractReceiveBuilder.() -> AbstractReceiveBuilder,
    private val filter: (Message) -> Boolean
): AbstractRule() {
    private var status: StatusReceiveBuilder = StatusReceiveBuilder.PASSED

    fun statusReceiveBuilder(): StatusReceiveBuilder = status

    override fun checkMessageFromConnection(message: Message): Boolean {
        if (filter.invoke(message)){
            status = FilterReceiveBuilder(message).invoke(filterReceive).statusReceiveBuilder
            if(status.eventStatus == Event.Status.PASSED) {
                return true
            }
        }
        return false
    }

    val foundFailOn: Boolean
        get() = status == StatusReceiveBuilder.FAILED
}