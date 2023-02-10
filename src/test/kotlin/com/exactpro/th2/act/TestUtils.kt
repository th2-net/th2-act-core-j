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

package com.exactpro.th2.act

import com.exactpro.th2.act.core.messages.IField
import com.exactpro.th2.act.core.requests.IRequest
import com.exactpro.th2.act.core.requests.Request
import com.exactpro.th2.act.core.rules.MessageFields
import com.exactpro.th2.act.core.rules.MessageFieldsCheckRule
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.common.value.toValue
import java.time.Instant
import org.apache.commons.lang3.RandomStringUtils

enum class TestMessageType(private val typeName: String) {

    NEW_ORDER_SINGLE("NewOrderSingle"),
    ORDER_CANCEL_REPLACE_REQUEST("OrderCancelReplaceRequest"),
    ORDER_CANCEL_REQUEST("OrderCancelRequest"),
    EXECUTION_REPORT("ExecutionReport"),
    BUSINESS_MESSAGE_REJECT("BusinessMessageReject"),
    REJECT("Reject"),
    ORDER_CANCEL_REJECT("OrderCancelReject"),
    TRADE_CAPTURE_REPORT("TradeCaptureReport"),
    TRADE_CAPTURE_REPORT_ACK("TradeCaptureReportAck");

    fun typeName() = typeName
}

enum class TestField(private val fieldName: String) : IField {

    // Basic Order Management Fields

    CLIENT_ORDER_ID("ClOrdID"),
    ORDER_TYPE("OrdType"),
    IOI_QUALIFIER("IOIQualifier"),
    SYMBOL("Symbol"),
    SECURITY_ID("SecurityID"),
    SECURITY_ID_SOURCE("SecurityIDSource"),
    BENCHMARK_SECURITY_ID("BenchmarkSecurityID"),
    BENCHMARK_SECURITY_ID_SOURCE("BenchmarkSecurityIDSource"),
    SIDE("Side"),
    PRICE("Price"),
    ORDER_QTY("OrderQty"),
    DISPLAY_QTY("DisplayQty"),
    MIN_QTY("MinQty"),
    TIME_IN_FORCE("TimeInForce"),
    PRE_TRADE_ANONYMITY("PreTradeAnonymity"),
    SETTL_TYPE("SettlType"),
    EXEC_INST("ExecInst"),
    ORDER_CAPACITY("OrderCapacity"),
    TRANSACT_TIME("TransactTime"),
    ENTERING_TRADER("EnteringTrader"),
    CLIENT_ID("ClientID"),
    EXECUTION_DECISION_MAKER("ExecutionDecisionMaker"),
    INVESTMENT_DECISION_MAKER("InvestmentDecisionMaker"),
    ORDER_ORIGINATION_TRADER("OrderOriginationTrader"),
    CLIENT_ID_TYPE("ClientIDType"),
    EXECUTION_DECISION_MAKER_TYPE("ExecutionDecisionMakerType"),
    INVESTMENT_DECISION_MAKER_TYPE("InvestmentDecisionMakerType"),
    BUSINESS_REJECT_REF_ID("BusinessRejectRefID"),
    REF_MESSAGE_TYPE("RefMsgType"),
    NO_PARTY_IDS("NoPartyIDs"),
    PARTY_ID_SOURCE("PartyIDSource"),
    PARTY_ROLE("PartyRole"),
    PARTY_ROLE_QUALIFIER("PartyRoleQualifier"),

    // Trade Matching Fields

    TRADE_REPORT_ID("TradeReportID"),
    TRADE_REPORT_STATUS("TrdRptStatus");

    override fun getFieldName() = fieldName
}

/**
 * Creates an [Array] of random [IField] to [String] pairs of the specified maximum amount. The length of the string
 * values can be specified.
 */
fun Int.randomFields(valueLength: Int = 10): Array<Pair<IField, String>> = Array(this) {
    TestField.values().random() to RandomStringUtils.randomAlphanumeric(valueLength)
}

/**
 * Creates an [Array] of random [String]s of this maximum amount. Values to be excluded can optionally be
 * specified.
 */
fun Int.randomMessageTypes(vararg exclude: String): Array<TestMessageType> = Array(this) {
    var messageType: TestMessageType
    do {
        messageType = randomMessageType()
    } while (exclude.contains(messageType.typeName()))

    messageType
}

/**
 * Returns an [Array] of this amount of elements created from the specified transformation.
 */
inline infix fun <reified T> Int.of(generator: (Int) -> T): Array<T> = Array(this) { generator(it) }

/**
 * Converts the receiver string to a [ConnectionID] object.
 */
fun String.toConnectionID(): ConnectionID = ConnectionID.newBuilder().setSessionAlias(this).build()

/**
 * Creates a [MessageFields] object with the specified message type and fields.
 */
fun TestMessageType.toMessageFields(vararg field: Pair<IField, String>): MessageFields =
    MessageFields(this.typeName(), field.associate { it })

/**
 * Creates a random [MessageFields] object with the specified message type.
 */
fun TestMessageType.toRandomMessageFields(fieldCount: Int = 10, valueLength: Int = 10): MessageFields =
    MessageFields(this.typeName(), fieldCount.randomFields(valueLength).associate { it })

/**
 * Creates a [Message] object with the receiver message type and the specified connection ID and fields.
 */
fun TestMessageType.toMessage(
    connectionID: ConnectionID,
    vararg field: Pair<IField, Any>,
    direction: Direction = Direction.FIRST,
    parentEventID: EventID? = null
): Message = toMessage(connectionID, field.toMap(), direction, parentEventID)

/**
 * Creates a [Message] object with the receiver message type and the specified connection ID and fields.
 */
fun TestMessageType.toMessage(
    connectionID: ConnectionID,
    fields: Map<IField, Any>,
    direction: Direction = Direction.FIRST,
    parentEventID: EventID? = null,
): Message {
    val metadata = MessageMetadata.newBuilder()
        .setMessageType(this.typeName())
        .setId(
            MessageID.newBuilder()
                .setConnectionId(connectionID)
                .setDirection(direction)
                .setBookName(DUMMY_BOOK_NAME)
                .setSequence(1)
                .setTimestamp(Instant.now().toTimestamp()))

    return Message.newBuilder()
        .setMetadata(metadata)
        .putAllFields(fields.asSequence()
            .map { (field, value) -> field.fieldName to value.toValue() }
            .toMap())
        .also { builder -> parentEventID?.let { builder.parentEventId = it } }.build()
}

/**
 * Creates a [Message] object of this message type, the specified connection ID and **random** fields.
 */
fun TestMessageType.toRandomMessage(
    connectionID: ConnectionID = randomConnectionID(),
    fieldCount: Int = 10,
    direction: Direction? = null,
    parentEventID: EventID? = null
): Message {
    return this.toMessage(
        connectionID = connectionID,
        fields = fieldCount.randomFields().toMap(),
        direction = direction ?: randomDirection(),
        parentEventID = parentEventID ?: randomString().toEventID()
    )
}

/**
 * Returns a [MessageFieldsCheckRule] for this connection ID and the specified [MessageFields].
 */
fun ConnectionID.toMessageFieldsCheckRule(vararg messageFields: MessageFields): MessageFieldsCheckRule {
    return MessageFieldsCheckRule(this, *messageFields)
}

/**
 * Returns a random [MessageFieldsCheckRule] for this connection ID and the specified [MessageFields].
 */
fun ConnectionID.toRandomMessageFieldsCheckRule(messageFieldsCount: Int = 10): MessageFieldsCheckRule {
    return this.toMessageFieldsCheckRule(
        *(Array(messageFieldsCount) { randomMessageType() }.distinct()
                .map { it.toRandomMessageFields() }
                .toTypedArray())
    )
}

/**
 * Returns a new [MessageBatch] only consisting of this message.
 */
fun Message.toBatch(): MessageBatch = MessageBatch.newBuilder().addMessages(this).build()

/**
 * Creates an [EventID] from this string.
 */
fun String.toEventID(): EventID = EventID.newBuilder()
    .setId(this)
    .setBookName(DUMMY_BOOK_NAME)
    .setScope(DUMMY_SCOPE_NAME)
    .build()

/**
 * Creates an [Event] from this event ID.
 */
fun randomEvent(): Event = Event.start().endTimestamp()

/**
 * Returns a random connection ID (Guaranteed to not be blank).
 */
fun randomConnectionID(): ConnectionID {
    val sessionAlias = RandomStringUtils.randomAlphanumeric(10)

    require(sessionAlias.isNotBlank())
    return sessionAlias.toConnectionID()
}

/**
 * Returns a random [String].
 */
fun randomMessageType(): TestMessageType = TestMessageType.values().random()

/**
 * Returns a random [Message]. The direction of the message can optionally be specified.
 */
fun randomMessage(connectionID: ConnectionID = randomConnectionID(),
                  direction: Direction? = null,
                  parentEventID: EventID? = null): Message {
    return randomMessageType().toRandomMessage(connectionID, direction = direction, parentEventID = parentEventID)
}

/**
 * Returns a random [String].
 */
fun randomString(stringLength: Int = 10): String = RandomStringUtils.randomAlphanumeric(stringLength)

/**
 * Returns a random [IField].
 */
fun randomField(): IField = TestField.values().random()

/**
 * Returns a random [Direction]. [Direction.UNRECOGNIZED] is excluded.
 */
fun randomDirection(): Direction = Direction.values().filter { it != Direction.UNRECOGNIZED }.random()

/**
 * Creates a random [IRequest].
 */
fun randomRequest(): IRequest = Request(
    requestMessage = randomMessage(), requestDescription = randomString()
)

/**
 * Returns a random [Event.Status]
 */
fun randomEventStatus(): Event.Status = Event.Status.values().random()

const val DUMMY_BOOK_NAME = "dummy"
const val DUMMY_SCOPE_NAME = "dummy"