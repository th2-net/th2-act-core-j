package com.exactpro.th2.act.dsl

import com.exactpro.th2.act.*
import com.exactpro.th2.act.core.dsl.`do`
import com.exactpro.th2.act.core.dsl.context
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.monitors.IMessageResponseMonitor
import com.exactpro.th2.act.stubs.StubMessageRouter
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        context(messageRouter, responseMonitor, subscriptionManager, mapOf("sessionAlias" to Direction.FIRST)) `do` {
            send(message, true)
        }
    }

    @Test
    fun `receive one message`() {
        val message = randomMessage()

        context(messageRouter, responseMonitor, subscriptionManager, mapOf("sessionAlias" to Direction.FIRST, "anotherSessionAlias" to Direction.FIRST)){ msg: Message ->
            msg.sessionAlias == "sessionAlias" && msg.direction == Direction.FIRST
        } `do` {
            send(message)
            receive {
                passOn("NewOrderSingle") {
                    direction == Direction.FIRST
                }
                failOn("NewOrderSingle") {
                    direction == Direction.SECOND
                }
                failOn("NewOrderSingle") {
                    sequence == message.sequence
                }
            }
        }
    }

    @Test
    fun `repeat until`() {
        val message = randomMessage()

        context(messageRouter, responseMonitor, subscriptionManager,  "sessionAlias", Direction.FIRST, "anotherSessionAlias") `do` {
            send(message)

            repeatUntil{ mes ->
                mes.sessionAlias == "sessionAlias"
            } `do` {
                receive{
                    passOn("NewOrderSingle") {
                        direction == Direction.FIRST
                    }
                    failOn("NewOrderSingle") {
                        direction == Direction.SECOND
                    }
                }
            }
        }
    }
}