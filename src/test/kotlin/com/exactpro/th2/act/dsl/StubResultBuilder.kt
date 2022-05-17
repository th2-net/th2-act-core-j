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

package com.exactpro.th2.act.dsl

import com.exactpro.th2.act.core.dsl.IResultBuilder
import com.exactpro.th2.common.grpc.Message

class StubResultBuilder: IResultBuilder {
    private val messages = mutableListOf<Message>()

    fun setSingleMessage(message: Message) {
        messages.add(message)
    }

    fun setListMessages(messages: List<Message>) {
        messages.forEach {
            this.messages.add(it)
        }
    }

    fun getMessage(i: Int): Message = messages[i]

    fun getMessages(): List<Message> = messages

    override fun onFailure(function: (Message, Message) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun setMessage(msg: Message): IResultBuilder {
        TODO("Not yet implemented")
    }

    override fun setErrorReason(errorMessage: Message): IResultBuilder {
        TODO("Not yet implemented")
    }

    override fun failure(msg: Message): IResultBuilder {
        TODO("Not yet implemented")
    }

    override fun success(): IResultBuilder {
        TODO("Not yet implemented")
    }

    override fun reason(s: String): IResultBuilder {
        TODO("Not yet implemented")
    }
}