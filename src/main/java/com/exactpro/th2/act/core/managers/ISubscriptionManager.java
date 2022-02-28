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

package com.exactpro.th2.act.core.managers;

import com.exactpro.th2.common.grpc.Direction;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.common.schema.message.MessageListener;

public interface ISubscriptionManager {
    /**
     * Registers the specified message listener to the given message direction. The listener will be invoked
     * whenever a new message batch is received for the specified direction.
     *
     * @param direction The direction of messages the listener should be registered for as a {@link Direction}.
     * @param listener  The listener to be registered as a {@link MessageListener}.
     */
    void register(Direction direction, MessageListener<MessageBatch> listener);

    /**
     * Unregisters the specified message listener from the given message direction.
     *
     * @param direction The direction of messages the listener should be unregistered from as a {@link Direction}.
     * @param listener  The listener to be unregistered as a {@link MessageListener}.
     */
    boolean unregister(Direction direction, MessageListener<MessageBatch> listener);
}
