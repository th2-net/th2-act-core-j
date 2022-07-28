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

package com.exactpro.th2.act.core.handlers.decorators

import com.exactpro.th2.act.core.action.FailedResponseFoundException
import com.exactpro.th2.act.core.monitors.IResponseCollector
import com.exactpro.th2.act.core.monitors.WaitingTasksBuffer
import com.exactpro.th2.act.core.receivers.MessagesReceiver
import com.exactpro.th2.act.core.requests.RequestContext
import com.exactpro.th2.act.core.response.IResponseProcessor
import com.exactpro.th2.act.core.rules.ReceiveRule

class ResponseReceiver(private val messagesReceiver: MessagesReceiver): AutoCloseable {

    fun handle(
        requestContext: RequestContext,
        responseProcessor: IResponseProcessor,
        timeout: Long,
        receiveRule: ReceiveRule,
        collector: IResponseCollector,
    ) {
        val task = WaitingTasksBuffer(receiveRule, collector)
        messagesReceiver.submitTask(task)
        task.await(timeout)

        responseProcessor.process(collector.responses, receiveRule.processedIDs(), requestContext)

        if(task.foundFailOn) {
            throw FailedResponseFoundException("Found a message for failOn.")
        }
    }

    fun cleanMessageBuffer(){
        messagesReceiver.cleanMessageBuffer()
    }

    override fun close(){
        messagesReceiver.close()
    }
}