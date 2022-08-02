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

package com.exactpro.th2.act.core.action

import com.exactpro.th2.act.core.managers.SubscriptionManager
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.routers.EventRouter
import com.exactpro.th2.act.core.routers.MessageRouter
import com.exactpro.th2.check1.grpc.Check1Service
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.EventID
import io.grpc.Context
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit.MILLISECONDS

class ActionFactory(
    private val messageRouter: MessageRouter,
    private val eventRouter: EventRouter,
    private val subscriptionManager: SubscriptionManager,
    private val check1Service: Check1Service
) {
    fun <T> createAction(
        observer: StreamObserver<T>,
        rpcName: String,
        requestName: String,
        parentEventID: EventID,
        timeout: Long = Context.current().deadline.timeRemaining(MILLISECONDS),
        messageBufferSize: Int = 1000
    ): ActionBuilder<T> {
        val requestContext = RequestContext(
            rpcName,
            requestName,
            messageRouter,
            eventRouter,
            parentEventID,
            Checkpoint.getDefaultInstance(),
            subscriptionManager,
            timeout
        )

        return ActionBuilder(observer, check1Service, requestContext, messageBufferSize)
    }
}