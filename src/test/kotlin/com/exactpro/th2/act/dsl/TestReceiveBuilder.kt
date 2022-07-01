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

package com.exactpro.th2.act.dsl

import com.exactpro.th2.act.core.dsl.FailedResponseFoundException
import com.exactpro.th2.act.core.dsl.ReceiveBuilder
import com.exactpro.th2.common.grpc.*
import com.exactpro.th2.common.message.sequence
import com.exactpro.th2.common.message.sessionAlias
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TestReceiveBuilder {

    @Test
    fun `test for ReceiveBuilder`() {
        val receiveBuilder = ReceiveBuilder(
            Message.newBuilder().setMetadata(
                MessageMetadata.newBuilder()
                    .setMessageType("NewOrderSingle")
                    .setId(
                        MessageID.newBuilder().setConnectionId(
                            ConnectionID.newBuilder().setSessionAlias("sessionAlias").build()
                        ).setDirection(Direction.FIRST)
                    )
            ).setParentEventId(EventID.newBuilder().setId("eventId").build()).apply { this.sequence = 1L }.build()
        )
        Assertions.assertEquals(
            true,
            receiveBuilder.passOn("NewOrderSingle") { this.sequence == 1L && this.sessionAlias == "sessionAlias" }
                .getStatus()
        )

        Assertions.assertEquals(
            false,
            receiveBuilder.passOn("NewOrderSingle") { this.sequence == 2L && this.sessionAlias == "anotherSessionAlias" }
                .getStatus()
        )
    }

    @Test
    fun `negative test for ReceiveBuilder`() {
        val receiveBuilder = ReceiveBuilder(
            Message.newBuilder().setMetadata(
                MessageMetadata.newBuilder()
                    .setMessageType("NewOrderSingle")
                    .setId(
                        MessageID.newBuilder().setConnectionId(
                            ConnectionID.newBuilder().setSessionAlias("sessionAlias").build()
                        ).setDirection(Direction.FIRST)
                    )
            ).setParentEventId(EventID.newBuilder().setId("eventId").build()).apply { this.sequence = 1L }.build()
        )
        val exception = assertThrows(FailedResponseFoundException::class.java){
            receiveBuilder.failOn("NewOrderSingle") { this.sequence == 1L && this.sessionAlias == "sessionAlias" }
                .getStatus()
        }
        Assertions.assertEquals("failOn", exception.message)
    }
}