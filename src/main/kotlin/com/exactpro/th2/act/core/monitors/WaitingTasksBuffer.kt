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

import com.exactpro.th2.act.core.rules.ReceiveRule
import com.exactpro.th2.common.grpc.Message
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class WaitingTasksBuffer(
    private val receiveRule: ReceiveRule,
    private val timeout: Long,
    private val monitor: IResponseMonitor
) {
    val foundFailOn: Boolean
        get() = receiveRule.statusReceive

    fun matchMessage(message: Message): Boolean =
        receiveRule.onMessage(message)

    fun responseReceived(message: Message) {
        monitor.responseMatch(message)
    }

    fun await() {
        LOGGER.info("Synchronization timeout: $timeout ms")
        val elapsed = measureTimeMillis { monitor.await(timeout, TimeUnit.MILLISECONDS) }
        LOGGER.info { "Waited for $elapsed millis" }
    }

    val isNotified: Boolean
        get() = monitor.isNotified
}