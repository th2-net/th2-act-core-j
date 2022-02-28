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

package com.exactpro.th2.act.core.response

import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.RequestStatus
import io.grpc.stub.StreamObserver
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

abstract class AbstractResponder<T>(private val observer: StreamObserver<T>): IResponder {

    private var completed = false

    override fun onResponseFound(status: RequestStatus.Status, checkpoint: Checkpoint, responses: List<Message>) {
        sendResponse(createResponse(status, checkpoint, responses))
    }

    override fun onError(cause: String) {
        sendResponse(createErrorResponse(cause))
    }

    override fun isResponseSent(): Boolean = completed

    /**
     * Sends the specified response message.
     *
     * @param response The response message as a [T].
     */
    private fun sendResponse(response: T) {

        LOGGER.info("Sending response to client: $response")

        check(!completed) { "The response was already sent" }

        completed = true
        observer.onNext(response)
        observer.onCompleted()
    }

    /**
     * Creates a response from the specified parameters.
     *
     * @param status The status of the response.
     * @param checkpoint The checkpoint for Check Service.
     * @param responses A [List] of found response messages.
     *
     * @return The response to be sent as a [T].
     */
    protected abstract fun createResponse(
        status: RequestStatus.Status, checkpoint: Checkpoint, responses: List<Message>
    ): T

    /**
     * Creates an error response with the specified message.
     *
     * @param cause The error message.
     *
     * @return The error response as a [T].
     */
    protected abstract fun createErrorResponse(cause: String): T
}
