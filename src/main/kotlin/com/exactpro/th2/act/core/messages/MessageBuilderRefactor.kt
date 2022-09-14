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
    message("MessageType") {
        metadata {
            protocol = "fix"
            addProperty("name", "value")
        }
        body {
            "field" to "simple value"
            "complex" to message {
                "field" to 1
            }
            "collection" to list[1,2,3,4]
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

fun message(type: String, setup: MessageBuilderRefactor.() -> Unit): Message = MessageBuilderRefactor(type).also(setup).build()


@MessageBuilderMarker
class MessageBuilderRefactor(type: String) {
    private val builder: Message.Builder = Message.newBuilder()
    init {
        builder.metadataBuilder.messageType = type
    }

    var parentEventId: EventID
        get() = builder.parentEventId
        set(value) {
            builder.parentEventId = value
        }

    val metadata: MessageMetadataBuilderRefactor = MessageMetadataBuilderRefactor(builder.metadataBuilder)

    val body: MessageBodyBuilderRefactor = MessageBodyBuilderRefactor(builder)

    operator fun MessageMetadataBuilderRefactor.invoke(action: MessageMetadataBuilderRefactor.() -> Unit) = action()

    operator fun MessageBodyBuilderRefactor.invoke(action: MessageBodyBuilderRefactor.() -> Unit): MessageBodyBuilderRefactor = apply(action)

    /**
     * Builds a [Message] according to the current state of this builder.
     */
    internal fun build(): Message = builder.build()
}


@MessageBuilderMarker
class MessageMetadataBuilderRefactor(
    private val metadataBuilder: MessageMetadata.Builder
) {
    var protocol: String
        get() = metadataBuilder.protocol
        set(value) {
            metadataBuilder.protocol = value
        }

    fun addProperty(name: String, value: String) {
        metadataBuilder.putProperties(name, value)
    }

    fun id (messageBlock: MessageIDBuilderRefactor.() -> Unit) {
        metadataBuilder.id = MessageIDBuilderRefactor().also(messageBlock).build()
    }
}

@BuilderMarker
class MessageIDBuilderRefactor{
    private val messageIDBuilder: MessageID.Builder = MessageID.newBuilder()

    var direction: Direction
        get() = messageIDBuilder.direction
        set(value) {
            messageIDBuilder.direction = value
        }

    var sessionAlias: String
        get() = messageIDBuilder.connectionId.sessionAlias
        set(value) {
            connectionId = ConnectionID.newBuilder().setSessionAlias(value).build()
        }

    var connectionId: ConnectionID
        get() = messageIDBuilder.connectionId
        set(value) {
            messageIDBuilder.connectionId = value
        }

    var sequence: Long
        get() = messageIDBuilder.sequence
        set(value) {
            messageIDBuilder.sequence = value
        }

    fun build(): MessageID = messageIDBuilder.build()
}

@MessageBuilderMarker
class MessageBodyBuilderRefactor(
    internal val messageBuilder: Message.Builder = Message.newBuilder(),
) {
    fun message(action: MessageBodyBuilderRefactor.() -> Unit): MessageBodyBuilderRefactor = MessageBodyBuilderRefactor(Message.newBuilder()).apply(action)

    val list: ListValueFactory = ListValueFactory

    object ListValueFactory {
        operator fun <T> get(vararg values: T): ListValueBuilderRefactor {
            return ListValueBuilderRefactor().apply { values.forEach(this::add) }
        }

        operator fun get(vararg messages: MessageBodyBuilderRefactor): ListValueBuilderRefactor {
            return ListValueBuilderRefactor().apply { messages.forEach(this::add) }
        }
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilderRefactor], where the value is *not* a nested structure.
     */
    infix fun String.to(value: Any?) {
        messageBuilder.putFields(this, value.toValue())
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilderRefactor], where the value is a nested body definition.
     */
    infix fun String.to(message: MessageBodyBuilderRefactor) {
        messageBuilder.putFields(this, message.messageBuilder.toValue())
    }

    infix fun String.to(list: ListValueBuilderRefactor) {
        messageBuilder.putFields(this, list.build().toValue())
    }

    infix fun String.buildList(action: ListValueBuilderRefactor.() -> Unit) {
        this to ListValueBuilderRefactor().apply(action)
    }

    fun build(): Message = messageBuilder.build()
}


@MessageBuilderMarker
class ListValueBuilderRefactor {

    private val listValueBuilder: ListValue.Builder = ListValue.newBuilder()

    fun addMessage(action: MessageBodyBuilderRefactor.() -> Unit) {
        add(MessageBodyBuilderRefactor().apply(action))
    }

    fun addValue(value: Any?) {
        add(value)
    }

    /**
     * Adds the specified body definition to this [ListValueBuilderRefactor].
     */
    internal fun add(body: MessageBodyBuilderRefactor) {
        listValueBuilder.addValues(body.messageBuilder.toValue())
    }

    /**
     * Adds the specified simple value to this [ListValueBuilderRefactor].
     */
    internal fun add(value: Any?) {
        listValueBuilder.addValues(value?.toValue() ?: NULL_VALUE)
    }

    /**
     * Builds a [ListValue] according to the current state of this [ListValueBuilderRefactor].
     */
    internal fun build(): ListValue = listValueBuilder.build()
}

private val NULL_VALUE = nullValue()
private fun Any?.toValue(): Value = this?.toValue() ?: NULL_VALUE