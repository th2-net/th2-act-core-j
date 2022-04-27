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

import com.exactpro.th2.act.core.response.IResponder
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.RequestStatus

class Responder : IResponder { //todo my class
    var responseMessages = mutableListOf<Message>() //TODO

    var responseIsSent: Boolean = false
    var sentStatus: RequestStatus.Status? = null

    override fun onError(cause: String) {//todo
        sentStatus = RequestStatus.Status.ERROR
    }

    override fun isResponseSent() = responseIsSent //todo

    override fun onResponseFound(
        status: RequestStatus.Status, checkpoint: Checkpoint, responses: List<Message>
    ) {
        for (msg in responses) {
            responseMessages.add(msg)
        }
    }
}