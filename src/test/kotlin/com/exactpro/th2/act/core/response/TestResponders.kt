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

import com.exactpro.th2.act.randomMessage
import com.exactpro.th2.act.randomString
import com.exactpro.th2.act.stubs.StubResponder
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.RequestStatus
import io.grpc.stub.StreamObserver
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

sealed class GTestResponder<T: Any> {

    /* Test List for responders:
     * 1) Should send a response to client when onResponseFound is called. V
     * 2) Should send an error response to the client when onError is called. V
     * 3) Should return that a response has been sent after sending a response. V
     */

    private lateinit var observer: StreamObserver<T>
    private lateinit var responder: IResponder
    private lateinit var responseMessages: List<Message>

    @BeforeEach
    internal fun setUp() {
        observer = mockk(relaxed = true)
        responder = createResponder(observer)
        responseMessages = createResponseMessages()
    }

    /**
     * Creates a test responder from the specified [StreamObserver].
     */
    abstract fun createResponder(observer: StreamObserver<T>): IResponder

    /**
     * Creates a list of response messages to be used during testing.
     */
    abstract fun createResponseMessages(): List<Message>

    /**
     * Verifies that .onNext(response) was called correctly on the stream observer when .onResponseFound() was invoked.
     */
    abstract fun verifyOnNextForOnResponseFound(
        observer: StreamObserver<T>, status: RequestStatus.Status, checkpoint: Checkpoint, responses: List<Message>
    )

    /**
     * Verifies that .onNext(response) was called correctly on the stream observer when .onError() was invoked.
     */
    abstract fun verifyOnNextForOnError(observer: StreamObserver<T>, cause: String)

    @Test
    fun `test should send response to client`() {
        val status = listOf(RequestStatus.Status.SUCCESS, RequestStatus.Status.ERROR).random()
        val checkpoint = Checkpoint.newBuilder().setId(randomString()).build()
        val responseMessages = createResponseMessages()

        responder.onResponseFound(status, checkpoint, responseMessages)

        verifyOnNextForOnResponseFound(observer, status, checkpoint, responseMessages)

        verify {
            observer.onCompleted()
        }
    }

    @Test
    fun `test should send error response to client`() {
        val cause = randomString()

        responder.onError(cause)
        verifyOnNextForOnError(observer, cause)

        verify {
            observer.onCompleted()
        }
    }

    @Test
    fun `test should mark response as sent`() {
        expect {
            that(responder.isResponseSent).isFalse()
        }

        responder.onResponseFound(
            listOf(RequestStatus.Status.SUCCESS, RequestStatus.Status.ERROR).random(),
            Checkpoint.newBuilder().setId(randomString()).build(),
            createResponseMessages()
        )

        expect {
            that(responder.isResponseSent).isTrue()
        }
    }

    @Test
    fun `test should mark response as sent after error`() {
        expect {
            that(responder.isResponseSent).isFalse()
        }

        responder.onError(randomString())

        expect {
            that(responder.isResponseSent).isTrue()
        }
    }
}

internal class TestPlaceMessageResponseAdapter: GTestResponder<StubResponder.Response>() {

    override fun createResponder(observer: StreamObserver<StubResponder.Response>) = StubResponder(observer)

    override fun createResponseMessages(): List<Message> = listOf(randomMessage())

    override fun verifyOnNextForOnResponseFound(
        observer: StreamObserver<StubResponder.Response>,
        status: RequestStatus.Status,
        checkpoint: Checkpoint,
        responses: List<Message>
    ) {
        val responseSlot = slot<StubResponder.Response>()
        verify { observer.onNext(capture(responseSlot)) }

        expect {
            that(responseSlot.captured) {
                get { status }.isEqualTo(status)
                get { checkpoint }.isEqualTo(checkpoint)
                get { responses }.isEqualTo(responses)
            }
        }
    }

    override fun verifyOnNextForOnError(observer: StreamObserver<StubResponder.Response>, cause: String) {
        val responseSlot = slot<StubResponder.Response>()
        verify { observer.onNext(capture(responseSlot)) }

        expect {
            that(responseSlot.captured) {
                get { status }.isEqualTo(RequestStatus.Status.ERROR)
                get { responses }.isEmpty()
            }
        }
    }
}
