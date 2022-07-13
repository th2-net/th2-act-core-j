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

package com.exactpro.th2.act.core.messages

import com.exactpro.th2.common.event.Event

enum class StatusMapping(val eventStatus: Event.Status) {
    FAILED(Event.Status.FAILED),
    PASSED(Event.Status.PASSED);
}

class MessageMapping(
    messageTypes: Collection<IMessageType>,
    private val checkExactMatch: Boolean,
    val statusMapping: StatusMapping
) {

    val messageTypes = messageTypes.map { messageType -> messageType.typeName }.sorted()

    /**
     * Checks if this [MessageMapping] matches the specified [Collection] of message types.
     * If [checkExactMatch] is `true`, the message types need to coincide in terms of quantity as well,
     * otherwise the specified collection will be considered a match if it is a superset of this mapping.
     *
     * @return `true` if this message mapping matches the specified collection of messages.
     */
    fun matches(actualMessageTypes: Collection<String>): Boolean {
        return if (checkExactMatch) actualMessageTypes.sorted() == messageTypes
        else actualMessageTypes.containsAll(messageTypes)
    }
}

/**
 * @return A [MessageMapping] that passes if a collection contains all of the specified message types.
 */
fun passedOn(vararg messageTypes: IMessageType): MessageMapping =
    MessageMapping(messageTypes.asList(), false, StatusMapping.PASSED)

/**
 * @return A [MessageMapping] that fails if a collection contains all of the specified message types.
 */
fun failedOn(vararg messageTypes: IMessageType): MessageMapping =
    MessageMapping(messageTypes.asList(), false, StatusMapping.FAILED)

/**
 * @return A [MessageMapping] that passes if a collection contains exactly the specified message types.
 */
fun passedOnExact(vararg messageTypes: IMessageType): MessageMapping =
    MessageMapping(messageTypes.asList(), true, StatusMapping.PASSED)

/**
 * @return A [MessageMapping] that fails if a collection contains exactly the specified message types.
 */
fun failedOnExact(vararg messageTypes: IMessageType): MessageMapping =
    MessageMapping(messageTypes.asList(), true, StatusMapping.FAILED)
