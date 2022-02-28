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
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.value.toValue

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
        builder.putAllFields(MessageBodyBuilder().also(bodyBlock).build())
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

    /**
     * Sets the message type for this metadata.
     */
    fun withMessageType(messageType: IMessageType) {
        withMessageType(messageType.typeName)
    }

    /**
     * Sets the message type (as a [String]) for this metadata.
     */
    fun withMessageType(messageType: String) {
        metadataBuilder.messageType = messageType
    }

    /**
     * Builds a [MessageMetadata] according to the current state of this builder.
     */
    fun build(): MessageMetadata = metadataBuilder.build()
}


@BuilderMarker
class MessageBodyBuilder {
    private val fields: MutableMap<String, Value> = mutableMapOf()

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
        fields[this] = value.toValue()
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
        fields[this] = MessageBuilder.create { body(bodyBlock) }.toValue()
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
        fields[this] = ListValueBuilder().also(listBlock).build().toValue()
    }

    /**
     * Builds a key value [Map] according to the current state of this [MessageBodyBuilder].
     */
    fun build(): Map<String, Value> = fields
}


@BuilderMarker
class ListValueBuilder {
    private val listValueBuilder: ListValue.Builder = ListValue.newBuilder()

    /**
     * Adds the specified body definition to this [ListValueBuilder].
     */
    fun add(bodyBlock: MessageBodyBuilder.() -> Unit) {
        listValueBuilder.addValues(MessageBuilder.create { body(bodyBlock) }.toValue())
    }

    /**
     * Adds the specified simple value to this [ListValueBuilder].
     */
    fun add(value: Any) {
        listValueBuilder.addValues(value.toValue())
    }

    /**
     * Builds a [ListValue] according to the current state of this [ListValueBuilder].
     */
    fun build(): ListValue = listValueBuilder.build()
}
