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

package com.exactpro.th2.act.core.monitors

import com.exactpro.th2.act.core.messages.MessageMatches
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface IResponseCollector : IMessageResponseMonitor {
    val responses: List<MessageMatches>
    fun responseMatch(message: MessageMatches)
}

abstract class AbstractResponseCollector : IResponseCollector {
    private val lock = ReentrantReadWriteLock()
    private val _responses: MutableList<MessageMatches> = arrayListOf()
    override val responses: List<MessageMatches>
        get() = lock.read { _responses.toList() }

    protected fun addResponse(message: MessageMatches): Unit = lock.write {
        if (isNotified) return@write // already notified, cannot add response
        _responses += message
    }
}