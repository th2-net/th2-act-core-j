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

package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.monitors.MessageResponseMonitor
import com.exactpro.th2.common.grpc.Message
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class WaitingTasksBuffer (
    private val receiveRule: ReceiveRule,
    private val timeout: Long,
    private val rpcName: String
) {
    private val monitor = MessageResponseMonitor()
    private lateinit var incomingMessage: Message

    private var elapsed: Long = 0

    fun onMessage(message: Message): Boolean {
        val math = receiveRule.onMessage(message)
        if (math) {
            incomingMessage = message
        }
        return math
    }

    fun monitorResponseReceived() {
        LOGGER.debug("Response Monitor notified in $elapsed ms for $rpcName")
        monitor.responseReceived()
    }

    fun await() {
        LOGGER.info("Synchronization timeout: $timeout ms")
        elapsed = measureTimeMillis { monitor.await(timeout, TimeUnit.MILLISECONDS) }
    }

    fun isNotified(): Boolean = monitor.isNotified

    fun incomingMessage(): Message = incomingMessage
}