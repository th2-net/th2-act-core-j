package com.exactpro.th2.act.dsl

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.dsl.context
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.common.grpc.ConnectionID
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sessionAlias
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isEqualTo

class TestDSL {

    private lateinit var messageBatchRouter: StubMessageRouter<MessageBatch>
    private lateinit var messageRouter: MessageRouter
    private lateinit var responseMonitor: IMessageResponseMonitor
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeEach
    internal fun setUp() {
        messageBatchRouter = StubMessageRouter()
        messageRouter = MessageRouter(messageBatchRouter)
        responseMonitor = mockk { justRun { responseReceived() } }
        subscriptionManager = spyk { }
    }

    @Test
    fun `send message and wait echo`() {
        val message = randomMessage()
        context(messageRouter, responseMonitor, subscriptionManager, mapOf("sessionAlias" to Direction.FIRST)) {

            val connectionID: ConnectionID = ConnectionID.newBuilder().setSessionAlias(message.sessionAlias).build()
            send(message, true)

            expect {
                that(messageBatchRouter.sent.messagesList.size).isEqualTo(1)
                that(messageBatchRouter.sent.messagesList.first()).apply {
                    get { metadata.id.connectionId }.isEqualTo(connectionID)
                    get { metadata.messageType }.isEqualTo(message.messageType)
                    get { fieldsMap }.isEqualTo(message.fieldsMap)
                }
            }
        }
    }

    @Test
    fun `receive one message`() {
        val message = randomMessage()
        context(messageRouter, responseMonitor, subscriptionManager, mapOf("sessionAlias" to Direction.FIRST)) {
            send(message)

            receive {
                passOn("NewOrderSingle") {
                    direction == Direction.FIRST
                }
                failOn("NewOrderSingle") {
                    direction == Direction.SECOND
                }
            }
        }
    }

    @Test
    fun `repeat until`() {
        val message = randomMessage()
        context(messageRouter, responseMonitor, subscriptionManager, "sessionAlias", "anotherSessionAlias"){
            val msg = send(message)

            repeatUntil (msg){ mes ->
                mes.sessionAlias == msg.sessionAlias
            } `do` {
                receive{
                    passOn("NewOrderSingle") {
                        msg.direction == Direction.FIRST
                    }
                    failOn("NewOrderSingle") {
                        msg.direction == Direction.SECOND
                    }
                }
            }
        }
    }

}