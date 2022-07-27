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
import java.util.concurrent.TimeUnit

/**
 * Monitor for waiting until all expected responses are found
 */
interface IMessageResponseMonitor {

    fun responseMatch(message: MessageMatches)

    /**
     * Blocks the current thread until this [IMessageResponseMonitor] is notified, or the specified timeout is elapsed.
     *
     * @param timeout  The timeout for the synchronization.
     * @param timeUnit The [TimeUnit] for the timeout.
     *
     * @throws InterruptedException If the blocked thread was interrupted while synchronizing.
     */
    fun await(timeout: Long, timeUnit: TimeUnit)

    /**
     * `true` if this {@link IResponseMonitor} has been notified that a response was received.
     */
    val isNotified: Boolean

    /**
     * Notifies this [IMessageResponseMonitor] that a response has been received.
     */
    fun responseReceived()
}