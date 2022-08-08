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

package com.exactpro.th2.act.core.routers

import com.exactpro.th2.act.core.messages.MessageMatches
import com.exactpro.th2.act.core.response.IBodyDataFactory
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.EventUtils.createMessageBean
import com.exactpro.th2.common.event.IBodyData
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.toTreeTable
import com.exactpro.th2.common.schema.message.MessageRouter
import com.fasterxml.jackson.core.JsonProcessingException
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import java.io.IOException

private val LOGGER = KotlinLogging.logger {}

class EventRouter(private val eventBatchRouter: MessageRouter<EventBatch>) {

    /**
     * Submits the specified event to the underlying event batch router.
     *
     * @param eventRequest The event to be submitted.
     * @param parentEventID The ID of the parent event.
     *
     * @throws IOException In the event count not be submitted.
     * @throws IllegalStateException If more than one Message Queue was found for the events.
     */
    fun storeEvent(eventRequest: Event, parentEventID: EventID? = null): EventID {
        val protoEvent = eventRequest.asProto(parentEventID)

        try {
            LOGGER.debug { "Storing event: ${TextFormat.shortDebugString(protoEvent)}" }
            eventBatchRouter.send(EventBatch.newBuilder().addEvents(protoEvent).build())
            return protoEvent.id
        } catch (e: Exception) {
            LOGGER.error(e) { "Could not store event with ID ${protoEvent.id}." }
            throw e
        }
    }

    /**
     * Submits all of the specified events to the underlying event batch router. The ID of the parent event can
     * optionally be specified.
     *
     * @throws IOException In the event count not be submitted.
     * @throws IllegalStateException If more than one Message Queue was found for the events.
     */
    fun storeEvents(eventRequest: Collection<Event>, parentEventId: EventID? = null) {
        val protoEvents = eventRequest.map { it.asProto(parentEventId) }

        try {
            LOGGER.debug {
                "Storing events: ${protoEvents.joinToString(",") { TextFormat.shortDebugString(it) }}"
            }

            val builder = EventBatch.newBuilder()
            if (parentEventId != null) {
                builder.parentEventId = parentEventId
            }
            eventBatchRouter.send(builder.addAllEvents(protoEvents).build())

        } catch (e: Exception) {
            LOGGER.error(e) { "Could not store events ${protoEvents.joinToString(",") { it.id.toString() }}." }
            throw e
        }
    }

    /**
     * Creates a parent estore event for the specified connection and method call.
     *
     * @param parentEventId The ID of the parent event as an [EventID]
     * @param rpcName         The name of the service method that is being called (Will be a part of the event name).
     * @param requestName The name of the class of the request.
     * @param status          The initial status of the event as a [Event.Status].
     * @param description Description of the event as a string.
     * @return The ID of the created event as an [EventID].
     *
     * @throws EventSubmissionException If an error occurs while creating the specified event.
     */
    fun createParentEvent(
        parentEventId: EventID,
        rpcName: String,
        requestName: String,
        status: Event.Status,
        description: String): EventID {
        return createEvent(
            status,
            type = rpcName,
            name = requestName,
            description = description,
            parentEventID = parentEventId
        )
    }

    /**
     * Creates a send message event from the specified message. The parent event can optionally be specified.
     *
     * @param message The message to be included in the event.
     * @param parentEventID The ID of the parent event as an [EventID]
     * @param description Description of the event as a string.
     *
     * @return The ID of the created event as an [EventID].
     *
     * @throws EventSubmissionException If an error occurs while creating the specified event.
     */
    fun createSendMessageEvent(message: Message, parentEventID: EventID? = null, description: String): EventID {
        return createEvent(
            Event.Status.PASSED,
            type = "Outgoing Message",
            name = "Send ${message.metadata.messageType} message to connectivity",
            body = listOf(message.toTreeTable()),
            parentEventID = parentEventID,
            description = description
        )
    }

    /**
     * Creates response received events from the specified message list. The parent event for these events can
     * optionally be specified.
     *
     * @param messages The [List] of received [Message]s to be included in the event.
     * @param eventStatus The status to be set for the response received events.
     * @param parentEventID The ID of the parent event as an [EventID]
     * @param description Description of the event as a string.
     *
     * @throws EventSubmissionException If an error occurs while creating the specified events.
     */
    fun createResponseReceivedEvents(
        messages: List<Message>, eventStatus: Event.Status, parentEventID: EventID? = null, description: String
    ) {
        val events = Array(messages.size) { messages[it] }.map { message ->
            Event.start()
                .name("Received a ${message.messageType} message")
                .type("Received Message")
                .status(eventStatus)
                .bodyData(message.toTreeTable())
                .messageID(message.metadata.id)
                .description(description)
        }

        try {
            storeEvents(events, parentEventID)
        } catch (e: IOException) {
            throw EventSubmissionException("Can not store events $events", e)
        }
    }

    /**
     * Creates a no response received event (i.e. The expected response was not received from the system).
     * The parent event can optionally be specified.
     *
     * @param noResponseBodyFactory The body data supplier for this event.
     * @param processedMessageIDs A [Collection] of [MessageID]s linked to this event.
     * @param parentEventID The ID of the parent event as an [EventID]
     *
     * @return The ID of the created event as an [EventID].
     *
     * @throws EventSubmissionException If an error occurs while creating the specified event.
     */
    fun createNoResponseEvent(
        noResponseBodyFactory: IBodyDataFactory,
        processedMessageIDs: Collection<MessageID>,
        parentEventID: EventID? = null
    ): EventID {
        return createEvent(
            Event.Status.FAILED,
            type = "Error",
            name = "No Response Received",
            description = "The expected response was not received.",
            body = noResponseBodyFactory.createBodyData(),
            parentEventID = parentEventID,
            linkedMessages = processedMessageIDs
        )
    }

    /**
     * Creates a no matching mapping event. The parent event can optionally be specified.
     *
     * @param messagesMatches A [Collection] of the actually received messages.
     * @param parentEventID The ID of the parent event as an [EventID]
     *
     * @return The Id of the created event as an [EventID].
     *
     * @throws EventSubmissionException If an error occurs while creating the specified event.
     */
    fun createNoMappingEvent(
        messagesMatches: Collection<MessageMatches>,
        parentEventID: EventID? = null
    ): EventID {
        val receivedMessageTypes = messagesMatches.map { it.message.messageType }
        val eventBodyData = messagesMatches.map {
            createMessageBean("${it.status} on messages: ${it.message.messageType}")
        }

        return createEvent(Event.Status.FAILED,
                           type = "Error",
                           name = "No matching message mapping was found.",
                           description = "No matching message mapping found for the messages $receivedMessageTypes.",
                           body = eventBodyData,
                           parentEventID = parentEventID,
                           linkedMessages = messagesMatches.map { it.message.metadata.id })
    }

    /**
     * Creates an error event with the specified cause. The parent event can optionally be specified.
     *
     * @param description The cause of the error as a string.
     * @param parentEventID The ID of the parent event as an [EventID]
     *
     * @return The ID of the created event as an [EventID].
     *
     * @throws EventSubmissionException If an error occurs while creating the specified event.
     */
    fun createErrorEvent(description: String, parentEventID: EventID? = null): EventID {
        return createEvent(
            Event.Status.FAILED,
            type = "Error",
            name = "An Error has occurred",
            description = description,
            parentEventID = parentEventID
        )
    }

    /**
     * Creates an event and submits it to the event batch router.
     *
     * @return The ID of the created event as an [EventID].
     *
     * @throws EventSubmissionException If an error occurs while creating the specified event.
     */
    private fun createEvent(
        status: Event.Status,
        type: String,
        name: String,
        description: String,
        body: Collection<IBodyData> = emptyList(),
        parentEventID: EventID? = null,
        linkedMessages: Collection<MessageID> = emptyList()
    ): EventID {

        val event = Event.start()
                .name(name)
                .description(description)
                .type(type)
                .bodyData(body)
                .status(status).also {
                    linkedMessages.forEach { messageID -> it.messageID(messageID) }
                }.endTimestamp()

        return try {
            storeEvent(event, parentEventID)
        } catch (e: IOException) {
            throw EventSubmissionException("Can not send event ${event.id}", e)
        }
    }

    /**
     * Converts the specified event to a gRPC event and returns it. The parent ID for the gRPC event
     * can be specified.
     */
    private fun Event.asProto(parentEventID: EventID?) = try {
            this.toProto(parentEventID)
        } catch (e: JsonProcessingException) {
            throw EventSubmissionException("Couldn't parse event $this.", e)
    }
}
