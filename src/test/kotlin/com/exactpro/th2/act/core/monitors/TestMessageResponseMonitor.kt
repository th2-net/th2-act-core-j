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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import strikt.api.expect
import strikt.assertions.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

internal class TestMessageResponseMonitor {

    /* Test List for MessageResponseMonitor:
     * 1) Should be notified when responseReceived() is invoked. V
     * 2) Should unblock thread waiting on awaitSync() when notified. V
     * 3) Should not be notified if responseReceived() wasn't invoked. V
     * 4) Should unblock after timeout if responseReceived() wasn't invoked. V
     */

    private lateinit var responseMonitor: MessageResponseMonitor

    @BeforeEach
    internal fun setUp() {
        responseMonitor = MessageResponseMonitor()
    }

    @Test
    fun `test should notify response monitor`() {
        responseMonitor.responseReceived()

        expect {
            that(responseMonitor.isNotified).isTrue()
        }
    }

    @Test
    @Timeout(1000, unit = TimeUnit.MILLISECONDS)
    fun `test should unblock waiting thread when notified`() {
        var elapsedTime: Long? = null

        val awaitThread = Thread {
            elapsedTime = measureTimeMillis { responseMonitor.await(100L, TimeUnit.MILLISECONDS) }
        }.also { it.start() }

        Thread.sleep(50L)
        responseMonitor.responseReceived()

        awaitThread.join()

        expect {
            that(elapsedTime).isNotNull().isLessThan(100L)
        }
    }

    @Test
    fun `test should not notify response monitor`() {
        expect {
            that(responseMonitor.isNotified).isFalse()
        }
    }

    @Test
    @Timeout(1000, unit = TimeUnit.MILLISECONDS)
    fun `test should not notify response monitor in case of timeout`() {
        responseMonitor.await(100, TimeUnit.MILLISECONDS)

        expect {
            that(responseMonitor.isNotified).isFalse()
        }
    }

    @Test
    @Timeout(1000, unit = TimeUnit.MILLISECONDS)
    fun `test should unblock thread after timeout when monitor isn't notified`() {
        val elapsedTime = measureTimeMillis { responseMonitor.await(100, TimeUnit.MILLISECONDS) }

        expect {
            that(elapsedTime).isGreaterThanOrEqualTo(100L)
        }
    }
}
