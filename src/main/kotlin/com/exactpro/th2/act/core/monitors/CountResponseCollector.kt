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
import mu.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CountResponseCollector private constructor(count: Int) : AbstractResponseCollector() {
    init {
        require(count > 0) { "count must be greater than zero but was $count" }
    }
    private val latch = CountDownLatch(count)

    override fun responseMatch(message: MessageMatches) {
        addResponse(message)
        latch.countDown()
    }

    override fun await(timeout: Long, timeUnit: TimeUnit) {
        if (this.isNotified) {
            LOGGER.info("Monitor has been notified before it has started to await a response.")
            return
        }

        if (!latch.await(timeout, timeUnit)) {
            LOGGER.info(
                "Timeout elapsed before monitor was notified. Timeout {} ms",
                TimeUnit.MILLISECONDS.convert(timeout, timeUnit)
            )
        }
    }

    override val isNotified: Boolean
        get() = latch.count <= 0

    override fun responseReceived(): Unit = latch.countDown()

    companion object {
        private val LOGGER = KotlinLogging.logger { }

        @JvmStatic
        fun singleResponse(): IResponseCollector = multipleResponses(1)

        @JvmStatic
        fun multipleResponses(count: Int) : IResponseCollector = CountResponseCollector(count)
    }

}