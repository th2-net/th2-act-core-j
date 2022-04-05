package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.common.grpc.Message

class ReceiveBuilder {
    enum class Status { PASSED, FAILED }

    private val fail = Status.FAILED
    private val pass = Status.PASSED


    fun passOn(message: Message, msgType: String, filter: Message.() -> Boolean): Status {
        return pass.on(message, msgType, filter)
    }

    fun failOn(message: Message, msgType: String, filter: Message.() -> Boolean): Status {
        return fail.on(message, msgType, filter)
    }

    fun Status.on(message: Message, msgType: String, filter: Message.() -> Boolean): Status {
        TODO()
    }
}