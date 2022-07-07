package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.common.grpc.Message
import mu.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Monitor for waiting until all expected responses are found
 */
interface ResponseMonitor {
    fun responseMatch(message: Message)
    fun await(time: Long, unit: TimeUnit)
    val isNotified: Boolean
}

/**
 * Collector for matched responses
 */
interface ResponseCollector : ResponseMonitor {
    val responses: List<Message>
}

abstract class AbstractResponseCollector : ResponseCollector {
    private val lock = ReentrantReadWriteLock()
    private val _responses: MutableList<Message> = arrayListOf()
    override val responses: List<Message>
        get() = lock.read { _responses.toList() }

    protected fun addResponse(message: Message): Unit = lock.write {
        if (isNotified) return@write // already notified, cannot add response
        _responses += message
    }
}

class CountResponseCollector private constructor(count: Int) : AbstractResponseCollector() {
    init {
        require(count > 0) { "count must be greater than zero but was $count" }
    }
    private val latch = CountDownLatch(count)

    override fun responseMatch(message: Message) {
        addResponse(message)
        latch.countDown()
    }

    override fun await(time: Long, unit: TimeUnit) {
        if (this.isNotified) {
            LOGGER.info("Monitor has been notified before it has started to await a response.")
            return
        }

        if (!latch.await(time, unit)) {
            LOGGER.info(
                "Timeout elapsed before monitor was notified. Timeout {} ms",
                TimeUnit.MILLISECONDS.convert(time, unit)
            )
        }
    }

    override val isNotified: Boolean
        get() = latch.count <= 0

    companion object {
        private val LOGGER = KotlinLogging.logger { }

        @JvmStatic
        fun singleResponse(): ResponseCollector = multipleResponses(1)

        @JvmStatic
        fun multipleResponses(count: Int) : ResponseCollector = CountResponseCollector(count)
    }

}