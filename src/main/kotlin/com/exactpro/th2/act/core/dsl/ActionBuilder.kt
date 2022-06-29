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

package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.common.grpc.Message
import io.grpc.stub.StreamObserver

class ActionBuilder<T>(
    private val observer: StreamObserver<T>,
    private val requestContext: RequestContext
) {
    private var preFilter: (Message) -> Boolean = { true }

    fun preFilter(
        preFilter: (Message) -> Boolean
    ): ActionBuilder<T> {
        this.preFilter = preFilter
        return this
    }

    fun execute(action: Action<T>.() -> Unit) {
        val responder = Responder()
        val receiverFactory =
            MessageReceiverFactory(requestContext.subscriptionManager, requestContext.parentEventID, preFilter)
        val responseReceiver = ResponseReceiver(receiverFactory)
        try {
            action.invoke(Action(observer, requestContext, responder, responseReceiver))
        } catch (e: Exception) {
            observer.onError(e)
        }
        observer.onCompleted()
    }
}