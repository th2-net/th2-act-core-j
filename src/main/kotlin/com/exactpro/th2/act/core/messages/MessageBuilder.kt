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

fun message(block: MessageBuilder.() -> Unit): Message {
    return MessageBuilder.create(block)
}

@DslMarker
annotation class BuilderMarker

@BuilderMarker
class MessageBuilder {
    private val builder: Message.Builder = Message.newBuilder()

    /**
     * Adds the [MessageMetadata] initialized using the specified code block to this [MessageBuilder].
     */
    fun metadata(metadataBlock: MessageMetadataBuilder.() -> Unit) {
        builder.metadata = MessageMetadataBuilder().also(metadataBlock).build()
    }

    /**
     * Appends all fields present in the [MessageBodyBuilder] initialized with the specified code block to this
     * [MessageBuilder].
     */
    fun body(bodyBlock: MessageBodyBuilder.() -> Unit) {
        MessageBodyBuilder(builder).also(bodyBlock)
    }

    var parentEventId: EventID
        get() = builder.parentEventId
        set(value) {
            builder.parentEventId = value
        }

    /**
     * Builds a [Message] according to the current state of this builder.
     */
    fun build(): Message = builder.build()

    companion object {
        /**
         * Creates a message according to the specified message body.
         */
        infix fun create(messageBlock: MessageBuilder.() -> Unit): Message = MessageBuilder().also(messageBlock).build()
    }
}


@BuilderMarker
class MessageMetadataBuilder {
    private val metadataBuilder: MessageMetadata.Builder = MessageMetadata.newBuilder()

    var messageType: String
        get() = metadataBuilder.messageType
        set(value) {
            metadataBuilder.messageType = value
        }

    var protocol: String
        get() = metadataBuilder.protocol
        set(value) {
            metadataBuilder.protocol = value
        }

    fun addProperty(name: String, value: String) {
        metadataBuilder.putProperties(name, value)
    }

    fun id (messageBlock: MessageIDBuilder.() -> Unit) {
        metadataBuilder.id = MessageIDBuilder().also(messageBlock).build()
    }

    /**
     * Builds a [MessageMetadata] according to the current state of this builder.
     */
    fun build(): MessageMetadata = metadataBuilder.build()
}



@BuilderMarker
class MessageIDBuilder{
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

@BuilderMarker
class MessageBodyBuilder(
     val messageBuilder: Message.Builder = Message.newBuilder()
) {
    fun message(action: MessageBodyBuilder.() -> Unit): MessageBodyBuilder = MessageBodyBuilder(Message.newBuilder()).apply(action)

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
     * Adds a key value pair to this [MessageBodyBuilder], where the value is *not* a nested structure.
     */
    infix fun IField.toValue(value: Any) {
        this.fieldName toValue value
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilder], where the value is *not* a nested structure.
     */
    infix fun String.toValue(value: Any) {
        messageBuilder.putFields(this, value.toValue())
    }

    /**
     * Adds a key value pair to this [MessageBodyBuilder], where the value is a nested body definition.
     */
    infix fun IField.toMap(bodyBlock: MessageBodyBuilder.() -> Unit) {
        this.fieldName toMap bodyBlock
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilder], where the value is a nested body definition.
     */
    infix fun String.toMap(bodyBlock: MessageBodyBuilder.() -> Unit) {
        messageBuilder.putFields(this, MessageBuilder.create { body(bodyBlock) }.toValue())
    }

    /**
     * Adds a key value pair to this [MessageBodyBuilder], where the value is a nested list definition.
     */
    infix fun IField.toList(listBlock: ListValueBuilder.() -> Unit) {
        this.fieldName toList listBlock
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilder], where the value is a nested list definition.
     */
    infix fun String.toList(listBlock: ListValueBuilder.() -> Unit) {
        messageBuilder.putFields(this, ListValueBuilder().also(listBlock).build().toValue())
    }

    /**
     * Adds a key (as a [String]) value pair to this [MessageBodyBuilder], where the value is *not* a nested structure.
     */
    infix fun String.to(value: Any) {
        messageBuilder.putFields(this, value.toValue())
    }

    infix fun String.to(message: MessageBodyBuilder) {
        messageBuilder.putFields(this, message.messageBuilder.toValue())
    }

    infix fun String.to(list: ListValueBuilder) {
        messageBuilder.putFields(this, list.build().toValue())
    }

    infix fun String.buildList(action: ListValueBuilder.() -> Unit) {
        this to ListValueBuilder().apply(action)
    }
}


@BuilderMarker
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
    fun add(body: MessageBodyBuilder) {
        listValueBuilder.addValues(body.messageBuilder.toValue())
    }

    fun add(bodyBlock: MessageBodyBuilder.() -> Unit) {
        listValueBuilder.addValues(MessageBuilder.create { body(bodyBlock) }.toValue())
    }

    /**
     * Adds the specified simple value to this [ListValueBuilder].
     */
    fun add(value: Any?) {
        listValueBuilder.addValues(value?.toValue() ?: nullValue())
    }

    /**
     * Builds a [ListValue] according to the current state of this [ListValueBuilder].
     */
    fun build(): ListValue = listValueBuilder.build()
}