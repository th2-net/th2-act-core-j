# th2 act core (0.0.1)

## Overview

act-core-j is a library that contains the basic parts for creating a custom **act** project.

## Usage

Use it as a dependency to have access to building blocks.

th2-act contains methods that will help to:

1. create *act gRPC server*
2. describe the logic on how to *send* and *receive* messages (including receiving multiple messages) 

Events on sended and received messages can be seen in GUI.

## Example

Example of writing the *placeOrderFIX* method for a typed act.
```kotlin
override fun placeOrderFIX(
    request: PlaceMessageRequestTyped,
    responseObserver: StreamObserver<PlaceMessageResponseTyped
) {
    actionFactory.createAction(
        responseObserver,
        "placeOrderFIX", //The name of the method of the request
        "Place order FIX", //The name of the method of the request
        request.parentEventId, //Request parent event ID
        10_000 //The timeout for the synchronization in milliseconds
    )
        .preFilter { msg -> //Act will store messages corresponding to this prefilter in its message cache
            msg.messageType != "Heartbeat" //The message type is not 'Heartbeat'
                    && msg.sessionAlias == request.metadata.id.connectionId.sessionAlias //SessionAlias matches the sessionAlias of the message you are looking for 
                    && msg.direction == Direction.FIRST //SessionAlias matches the sessionAlias of the message you are looking for 
        }
        .execute {
            val requestMessage = convertorsRequest.createMessage(request) //Get a message from a typed request

            val checkpoint: Checkpoint =
                registerCheckPoint(requestMessage.parentEventId, request.description) //Checkpoint registration

            send(requestMessage, requestMessage.sessionAlias) //Send message

            val receiveMessage = receive(
                10_000,
                requestMessage.sessionAlias,
                Direction.FIRST
            ) { //Receiving one message corresponding to the condition with a wait of 10 seconds
                passOn("ExecutionReport") { //The type of message success ExecutionReport
                    fieldsMap["ClOrdID"] == requestMessage.fieldsMap["ClOrdID"] //ClOrdID of response message should be equal to this field in request message
                }
                failOn("BusinessMessageReject") { //The type of message success ExecutionReport
                    fieldsMap["BusinessRejectRefID"] == requestMessage.fieldsMap["ClOrdID"] //ClOrdID of response message should be equal to this field in request message
                }
            }

            val placeMessageResponseTyped: PlaceMessageResponseTyped = //Create PlaceMessageResponse object
                PlaceMessageResponseTyped.newBuilder()
                    .setResponseMessageTyped(convertorsResponse.createResponseMessage(receiveMessage))
                    .setStatus(RequestStatus.newBuilder().setStatus(RequestStatus.Status.SUCCESS))
                    .setCheckpointId(checkpoint)
                    .build()

            emitResult(placeMessageResponseTyped) //Transmitting the created placeMessageResponseTyped StreamObserver
        }
}