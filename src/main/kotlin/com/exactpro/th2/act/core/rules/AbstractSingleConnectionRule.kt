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

import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageID
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val LOGGER = KotlinLogging.logger {}

abstract class AbstractSingleConnectionRule(private val connectionID: ConnectionID): ICheckRule {

    private val messageIDList: MutableList<MessageID> = ArrayList()
    private val response = AtomicReference<Message?>(null)

    init {
        require(!StringUtils.isBlank(connectionID.sessionAlias)) {
            "'sessionAlias' in 'connectionID' must not be blank."
        }
    }

    override fun onMessage(message: Message): Boolean {
        if (checkSessionAlias(message)) {

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
                        "Previous Matched Message: ${TextFormat.shortDebugString(message)}"
                    }
                }
            }
            return match
        }
        return false
    }

    override fun processedIDs(): List<MessageID> = messageIDList

    override fun getResponse(): Message? = response.get()

    /**
     * Checks the specified message to see if it matches any of the [MessageFields].
     *
     * @param message The [Message] to be checked.
     * @return `true` if the specified message matches at least one rule, `false` otherwise.
     */
    protected abstract fun checkMessageFromConnection(message: Message): Boolean

    /**
     * Checks the session alias of the specified message and returns `true` if it is equal to the one expected.
     */
    private fun checkSessionAlias(message: Message): Boolean {

        val actualSessionAlias = message.metadata.id.connectionId.sessionAlias
        val expectedSessionAlias = connectionID.sessionAlias
        val match = (expectedSessionAlias == actualSessionAlias)

        LOGGER.debug(
            "Received message with session alias: ${actualSessionAlias}\n" +
            "Expected: ${connectionID.sessionAlias}, Match=${match}"
        )

        return match
    }
}
