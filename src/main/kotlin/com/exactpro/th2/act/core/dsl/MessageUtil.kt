package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.messages.IMessageType

enum class MessageType(private val typeName: String) : IMessageType {
    NEW_ORDER_SINGLE("NewOrderSingle"),
    ORDER_CANCEL_REPLACE_REQUEST("OrderCancelReplaceRequest"),
    ORDER_CANCEL_REQUEST("OrderCancelRequest"),
    EXECUTION_REPORT("ExecutionReport"),
    BUSINESS_MESSAGE_REJECT("BusinessMessageReject"),
    REJECT("Reject"),
    ORDER_CANCEL_REJECT("OrderCancelReject"),
    TRADE_CAPTURE_REPORT("TradeCaptureReport"),
    TRADE_CAPTURE_REPORT_ACK("TradeCaptureReportAck"),
    QUOTE_STATUS_REPORT("QuoteStatusReport"),
    DQ126("DQ126");

    override fun getTypeName() = typeName

    companion object {
        fun getMessageType(typeName: String): MessageType? = values().find { it.typeName == typeName }
    }
}