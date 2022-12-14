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
package com.exactpro.th2.act.core.rules;

import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageID;

import javax.annotation.Nullable;
import java.util.Collection;

public interface ICheckRule {

    /**
     * Checks if the messages matches the rule
     *
     * @param message the message to check
     * @return `true` if message matches the rule Otherwise, returns `false`
     */
    boolean onMessage(Message message);

    /**
     * The collections of {@link MessageID} that was processed by rule
     */
    Collection<MessageID> processedIDs();

    /**
     * Matched response
     *
     * @return the matched response or {@code null}
     */
    @Nullable
    Message getResponse();
}
