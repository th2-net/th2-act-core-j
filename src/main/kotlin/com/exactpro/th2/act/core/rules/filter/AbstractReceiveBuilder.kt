package com.exactpro.th2.act.core.rules

import com.exactpro.th2.act.core.rules.filter.IReceiveBuilder
import com.exactpro.th2.act.core.messages.IMessageType
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.grpc.Message

enum class StatusReceiveBuilder(val eventStatus: Event.Status) {
    PASSED(Event.Status.PASSED),
    OTHER(Event.Status.FAILED),
    FAILED(Event.Status.FAILED)
}

abstract class AbstractReceiveBuilder: IReceiveBuilder {
    protected val msgTypes = HashSet<IMessageType>()
    protected var status = StatusReceiveBuilder.PASSED

    val messageTypes: List<IMessageType> = msgTypes.toList()

    val statusReceiveBuilder: StatusReceiveBuilder
        get() = status

    operator fun invoke(filter: AbstractReceiveBuilder.() -> AbstractReceiveBuilder) = filter()

    abstract override fun passOn(msgType: String, filter: Message.() -> Boolean): AbstractReceiveBuilder
    abstract override fun failOn(msgType: String, filter: Message.() -> Boolean): AbstractReceiveBuilder
}