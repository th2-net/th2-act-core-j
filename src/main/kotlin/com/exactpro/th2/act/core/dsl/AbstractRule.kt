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

package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.rules.ICheckRule
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.message.toJson
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicReference

private val LOGGER = KotlinLogging.logger {}

abstract class AbstractRule: ICheckRule {

    private val messageIDList: MutableList<MessageID> = ArrayList()
    private val response = AtomicReference<Message?>(null)

    override fun onMessage(message: Message): Boolean {
        messageIDList.add(message.metadata.id)
        val match = checkMessageFromConnection(message)

        if (match) {
            if (response.compareAndSet(null, message)) {
                LOGGER.debug {
                    "Message matches the rule ${javaClass.simpleName}.\n" +
                            "Message: ${TextFormat.shortDebugString(message)}"
                }
            } else {
                LOGGER.warn {
                    "Rule matched to more than one response message.\n" +
                            "Previous Matched Message: ${message.toJson()}"
                }
            }
        }
        return match
    }

    override fun processedIDs(): List<MessageID> = messageIDList

    override fun getResponse(): Message? = response.get()

    protected abstract fun checkMessageFromConnection(message: Message): Boolean
}