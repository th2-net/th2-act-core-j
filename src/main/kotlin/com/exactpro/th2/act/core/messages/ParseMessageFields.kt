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

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.value.getMessage

/**
 * Retrieves the field with the specified name from this message.
 *
 * @receiver The message from which the field should be taken.
 *
 * @param fieldName The name of the field as a [String].
 *
 * @return The value of the field as a String.
 *
 * @throws MissingFieldException if the specified field is not present in the message.
 * @throws NullPointerException if the value for the specified field is null.
 */
fun Message.getStringField(fieldName: String): String {

    return if (this.containsFields(fieldName)) {
        this.fieldsMap[fieldName]!!.simpleValue

    } else {

        this.fieldsMap.forEach { (_, value) ->

            if (value.hasListValue()) {
                value.listValue.valuesList.forEach { listValue ->

                    if (listValue.hasMessageValue()) {

                        try {
                            val fieldValue: String? = listValue.getMessage()?.getStringField(fieldName)
                            if (fieldValue != null) return fieldValue
                        } catch (e: MissingFieldException) { // The field was not present.
                        }
                    }
                }
            } else if (value.hasMessageValue()) {
                val fieldValue: String? = value.getMessage()?.getStringField(fieldName)
                if (fieldValue != null) return fieldValue
            }
        }
        throw MissingFieldException("The field \"$fieldName\" is not present in the message: $this.")
    }
}

/**
 * Retrieves the field with the specified name from this message.
 *
 * @receiver The message from which the field should be taken.
 *
 * @param field The field as a [IField].
 *
 * @return The value of the field as a String.
 *
 * @throws MissingFieldException if the specified field is not present in the message.
 * @throws NullPointerException if the value for the specified field is null.
 */
fun Message.getStringField(field: IField): String = this.getStringField(field.fieldName)
