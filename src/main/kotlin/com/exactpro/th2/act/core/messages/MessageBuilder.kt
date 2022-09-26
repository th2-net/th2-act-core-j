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

import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.value.nullValue
import com.exactpro.th2.common.value.toValue

@DslMarker
annotation class MessageBuilderMarker

fun main() {
    val connectionID = ConnectionID.getDefaultInstance()
    message("MessageType", connectionID) {
        metadata {
            protocol = "fix"
            addProperty("name", "value")
            // or
            properties = mapOf(
                "name" to "value"
            )
        }
        body {
            "field" to "simple value"
            "complex" to message {
                "field" to 1
            }
            "collection" to list[1, 2, 3, 4]
            "complexCollection" to list[
                    message {
                        "field" to 'a'
                    },
                    message {
                        "field" to 'b'
                    }
            ]
            "anotherCollection" buildList {
                addMessage {
                    "a" to 'b'
                }

                addMessage {
                    "a" to 'c'
                }
            }

            "anotherAnotherCollection" buildList {
                addValue("a")
                addValue("c")
            }
        }
    }
}

fun message(type: String, connectionID: ConnectionID, setup: MessageBuilder.() -> Unit): Message =
    MessageBuilder(type, connectionID).also(setup).build()


@MessageBuilderMarker
class MessageBuilder(type: String, connectionID: ConnectionID) {
    private val builder: Message.Builder = Message.newBuilder()

    init {
        builder.metadataBuilder.messageType = type
        builder.metadataBuilder.setId(MessageID.newBuilder().setConnectionId(connectionID))
    }

    var parentEventId: EventID
        get() = builder.parentEventId
        set(value) {
            builder.parentEventId = value
        }

    val metadata: MessageMetadataBuilder = MessageMetadataBuilder(builder.metadataBuilder)

    val body: MessageBodyBuilder = MessageBodyBuilder(builder)

    operator fun MessageMetadataBuilder.invoke(action: MessageMetadataBuilder.() -> Unit) = action()

    operator fun MessageBodyBuilder.invoke(action: MessageBodyBuilder.() -> Unit): MessageBodyBuilder =
        apply(action)

    /**
     * Builds a [Message] according to the current state of this builder.
     */
    internal fun build(): Message = builder.build()
}


@MessageBuilderMarker
class MessageMetadataBuilder(
    private val metadataBuilder: MessageMetadata.Builder
) {
    var protocol: String
        get() = metadataBuilder.protocol
        set(value) {
            metadataBuilder.protocol = value
        }

    var direction: Direction
        get() = metadataBuilder.idBuilder.direction
        set(value) {
            metadataBuilder.idBuilder.direction = value
        }

    var sequence: Long
        get() = metadataBuilder.idBuilder.sequence
        set(value) {
            metadataBuilder.idBuilder.sequence = value
        }

    fun addProperty(name: String, value: String) {
        metadataBuilder.putProperties(name, value)
    }

    var properties: Map<String, String>
        get() = metadataBuilder.propertiesMap
        set(value) {
            metadataBuilder.clearProperties()
            metadataBuilder.putAllProperties(value)
        }
}

@MessageBuilderMarker
class MessageBodyBuilder(
    internal val messageBuilder: Message.Builder = Message.newBuilder(),
) {
    fun message(action: MessageBodyBuilder.() -> Unit): MessageBodyBuilder =
        MessageBodyBuilder(Message.newBuilder()).apply(action)

    val list: ListValueFactory = ListValueFactory

    object ListValueFactory {
        operator fun <T> get(vararg values: T): ListValueBuilder {
            return ListValueBuilder().apply { values.forEach(this::add) }
        }

        operator fun get(vararg messages: MessageBodyBuilder): ListValueBuilder {
            return ListValueBuilder().apply { messages.forEach(this::add) }
        }
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilder], where the value is *not* a nested structure.
     */
    infix fun String.to(value: Any?) {
        messageBuilder.putFields(this, value.toValue())
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilder], where the value is a nested body definition.
     */
    infix fun String.to(message: MessageBodyBuilder) {
        messageBuilder.putFields(this, message.messageBuilder.toValue())
    }

    infix fun String.to(list: ListValueBuilder) {
        messageBuilder.putFields(this, list.build().toValue())
    }

    infix fun String.buildList(action: ListValueBuilder.() -> Unit) {
        this to ListValueBuilder().apply(action)
    }

    fun build(): Message = messageBuilder.build()
}


@MessageBuilderMarker
class ListValueBuilder {

    private val listValueBuilder: ListValue.Builder = ListValue.newBuilder()

    fun addMessage(action: MessageBodyBuilder.() -> Unit) {
        add(MessageBodyBuilder().apply(action))
    }

    fun addValue(value: Any?) {
        add(value)
    }

    /**
     * Adds the specified body definition to this [ListValueBuilder].
     */
    internal fun add(body: MessageBodyBuilder) {
        listValueBuilder.addValues(body.messageBuilder.toValue())
    }

    /**
     * Adds the specified simple value to this [ListValueBuilder].
     */
    internal fun add(value: Any?) {
        listValueBuilder.addValues(value.toValue())
    }

    /**
     * Builds a [ListValue] according to the current state of this [ListValueBuilder].
     */
    internal fun build(): ListValue = listValueBuilder.build()
}

private val NULL_VALUE = nullValue()
private fun Any?.toValue(): Value = this?.toValue() ?: NULL_VALUE