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

import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageMetadata
import com.exactpro.th2.common.value.toValue

typealias MessageFieldMap = Map<*, *>
typealias RepeatingGroup = Iterable<*>

/**
 * Creates a [Message] object from this [MessageFieldMap]. The [String] to be added to the metadata can be
 * optionally specified.
 */
fun MessageFieldMap.toMessage(messageType: String? = null): Message {

    val wrappedFields = this.entries.associate { fieldEntry ->

        if (fieldEntry.key!! !is IField) {
            throw RuntimeException(
                "All keys in a message field map need to be an instance of an ${IField::javaClass.name}.\n" +
                "Received an instance of: ${fieldEntry.key!!.javaClass.simpleName}."
            )
        }

        val fieldName = (fieldEntry.key as IField).fieldName

        when (val fieldValue = fieldEntry.value) {
            is String, is Int, is Float, is Double -> fieldName to fieldValue.toValue()
            is MessageFieldMap -> fieldName to fieldValue.toMessage().toValue()
            is RepeatingGroup -> {

                val wrappedGroups = ListValue.newBuilder().addAllValues(fieldValue.map { group ->

                    when(group) {
                        is MessageFieldMap -> group.toMessage().toValue()
                        is String, is Int, is Float, is Double -> group.toValue()
                        else -> throw RuntimeException(
                            "All members of a repeating group should be a message field map or a simple value.\n" +
                            "Received an instance of: ${group!!.javaClass.simpleName}."
                        )
                    }

                }).build()

                fieldName to wrappedGroups.toValue()
            }
            else -> throw RuntimeException(
                "Cannot wrap an instance of ${fieldValue!!.javaClass.simpleName} for the field: $fieldName."
            )
        }
    }

    val builder = Message.newBuilder().putAllFields(wrappedFields)

    if (messageType != null) {
        builder.metadata = MessageMetadata.newBuilder().setMessageType(messageType).build()
    }

    return builder.build()
}
