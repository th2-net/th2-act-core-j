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

package com.exactpro.th2.act.core.monitors;

public interface IMessageResponseMonitor {

    /**
     * Notifies this {@link IMessageResponseMonitor} that a response has been received.
     */
    void responseReceived();

    /**
     * @return `true` if this {@link IMessageResponseMonitor} has been notified that a response was received.
     */
    boolean isNotified();
}
