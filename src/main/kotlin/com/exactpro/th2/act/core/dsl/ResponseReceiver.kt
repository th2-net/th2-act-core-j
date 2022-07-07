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
import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.act.core.response.IResponseProcessor

class ResponseReceiver(
    messageReceiverFactory: MessageReceiverFactory
) {
    private val messagesReceiver = messageReceiverFactory.create()

    fun handle(
        responder: IResponder,
        requestContext: RequestContext,
        responseProcessor: IResponseProcessor,
        timeout: Long,
        receiveRule: ReceiveRule,
        collector: ResponseCollector,
    ) {

        val task = WaitingTasksBuffer(receiveRule, timeout, collector)
        messagesReceiver.submitTask(task)
        task.await()

        responseProcessor.process(
            collector.responses, receiveRule.processedIDs() /*every message touched by the rule will be there*/, responder, requestContext
        )
    }

    fun cleanMessageBuffer(){
        messagesReceiver.cleanMessageBuffer()
    }

    fun close(){
        messagesReceiver.close()
    }
}