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

package com.exactpro.th2.act.stubs

import com.exactpro.th2.common.schema.message.MessageListener
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.common.schema.message.MessageRouterContext
import com.exactpro.th2.common.schema.message.configuration.MessageRouterConfiguration
import com.exactpro.th2.common.schema.message.impl.rabbitmq.connection.ConnectionManager

class StubMessageRouter<T: Any>: MessageRouter<T> {

    private var throws: Exception? = null

    lateinit var sent: T
    lateinit var queueAttributes: Array<out String>

    /**
     * Will throw the specified [Exception]] if set, on any call to send a message.
     */
    infix fun throws(exception: () -> Exception) {
        throws = exception()
    }

    /**
     * Throws an exception if configured so, otherwise stores the provided message and queue attributes.
     */
    override fun send(message: T) = sendAll(message)

    override fun send(message: T, vararg queueAttr: String) = sendAll(message, *queueAttr)

    override fun sendAll(message: T, vararg queueAttr: String)  {
        throws?.let {
            throw it
        }

        sent = message
        queueAttributes = queueAttr
    }

    // -------------------------------------------------------------------------------------------------------------- //

    override fun close() = fail()

    override fun init(connectionManager: ConnectionManager, configuration: MessageRouterConfiguration) = fail()

    override fun init(context: MessageRouterContext) = fail()

    override fun subscribe(callback: MessageListener<T>, vararg queueAttr: String) = fail()

    override fun subscribeAll(callback: MessageListener<T>) = fail()

    override fun subscribeAll(callback: MessageListener<T>, vararg queueAttr: String) = fail()
}
