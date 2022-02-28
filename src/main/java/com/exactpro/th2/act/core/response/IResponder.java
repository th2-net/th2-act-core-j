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

package com.exactpro.th2.act.core.response;

import com.exactpro.th2.common.grpc.Checkpoint;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.RequestStatus;

import java.util.List;

public interface IResponder {

    /**
     * Sends a response to the client whenever a message is found in response to an action.
     *
     * @param status     The status that should be set for the user.
     * @param checkpoint The checkpoint for Check Service.
     * @param responses  A {@link List} of found response messages.
     */
    void onResponseFound(RequestStatus.Status status, Checkpoint checkpoint, List<Message> responses);

    /**
     * Sends the specified error message to the client.
     *
     * @param cause The cause of the error.
     */
    void onError(String cause);

    /**
     * @return `true` if a response has been sent to the client using this responder, `false` otherwise.
     */
    boolean isResponseSent();
}
