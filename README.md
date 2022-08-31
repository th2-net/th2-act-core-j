# th2 act core (0.0.1)

## Overview

This is a act-core-j library that contains the basic parts for creating a custom **act** project.

## Use to create an act

>Full code of this ActHandler can be found [here](https://github.com/th2-net/th2-act-template-j/tree/act-typed).

To implement act using this library, you need to:

1. add dependency on `com.exactpro.th2:act-core:0.0.1-dev-2818111882-SNAPSHOT` into `build.gradle`
2. implement ActMain
    1. create a `CommonFactory`, `GrpcRouter`, `MessageRouter<MessageBatch>`, `SubscriptionManager`
   ```java
      CommonFactory factory = CommonFactory.createFromArguments(args);
      GrpcRouter grpcRouter = factory.getGrpcRouter();
      MessageRouter<MessageBatch> batchMessageRouter = factory.getMessageRouterParsedBatch();
      SubscriptionManager subscriptionManager = new SubscriptionManager();
   ```
   2. create an `ActionFactory` and an `ActHandler` (or ActHandlerTyped)
   ```java
      ActionFactory actionFactory = new ActionFactory(
          new com.exactpro.th2.act.core.routers.MessageRouter(batchMessageRouter),
          new com.exactpro.th2.act.core.routers.EventRouter(factory.getEventBatchRouter()),
          subscriptionManager,
          grpcRouter.getService(Check1Service.class)
      );
      
      ActHandler actHandler = new ActHandler(actionFactory);
   ```
   3. create an `ActServer` and start actHandler of service
   ```java
      ActServer actServer = new ActServer(grpcRouter.startServer(actHandler));
   ```
3. implement an `ActHandler` inheritable class ActImplBase (or ActHandlerTyped from the ActTypedImplBase)
   1. creating an `ActHandler`:
   ```kotlin
      class ActHandler(
         private val actionFactory: ActionFactory
      ) : ActGrpc.ActImplBase() {
         override fun placeOrderFIX(
            request: PlaceMessageRequest,
            responseObserver: StreamObserver<PlaceMessageResponse>
         ) {}
      }
   ```
   2. creating an `action`:
   ```kotlin
       override fun placeOrderFIX(
          request: PlaceMessageRequest,
          responseObserver: StreamObserver<PlaceMessageResponse>
       ) {
       actionFactory.createAction(
           responseObserver,
           "placeOrderFIX", //The name of the method of the request
           "Place order FIX", //The name of the method of the request
           request.parentEventId, //Request parent event ID
           10_000 //The timeout for the synchronization in milliseconds
       )
       }
   ``` 
   3. adding a `preFilter`, according to which the act saves messages in its message cache:
   ```kotlin
       override fun placeOrderFIX(
        request: PlaceMessageRequest,
        responseObserver: StreamObserver<PlaceMessageResponse>
       ) {
       actionFactory.createAction(
           responseObserver,
           "placeOrderFIX", //The name of the method of the request
           "Place order FIX", //The name of the method of the request
           request.parentEventId, //Request parent event ID
           10_000 //The timeout for the synchronization in milliseconds
       )
           .preFilter { msg -> 
               msg.messageType != "Heartbeat" //The message type is not 'Heartbeat'
                       && msg.sessionAlias == request.message.metadata.id.connectionId.sessionAlias //SessionAlias matches the sessionAlias of the message you are looking for
                       && msg.direction == Direction.FIRST //SessionAlias matches the sessionAlias of the message you are looking for
           }
       }
   ``` 
   4. add logic for sending and receiving messages in `execute`:
   ```kotlin
      override fun placeOrderFIX(
         request: PlaceMessageRequest,
         responseObserver: StreamObserver<PlaceMessageResponse>
      ) {
         actionFactory.createAction(
            responseObserver,
            "placeOrderFIX", //The name of the method of the request
            "Place order FIX", //The name of the method of the request
            request.parentEventId, //Request parent event ID
            10_000 //The timeout for the synchronization in milliseconds
         )
            .preFilter { msg ->
               msg.messageType != "Heartbeat" //The message type is not 'Heartbeat'
                       && msg.sessionAlias == request.message.metadata.id.connectionId.sessionAlias //SessionAlias matches the sessionAlias of the message you are looking for
                       && msg.direction == Direction.FIRST //SessionAlias matches the sessionAlias of the message you are looking for
            }
            .execute {
               val requestMessage = request.message //Get a message from a request
      
               val checkpoint: Checkpoint =
                  registerCheckPoint(requestMessage.parentEventId, request.description) //Checkpoint registration
      
               send(requestMessage, requestMessage.sessionAlias) //Send message
      
               val receiveMessage = receive(
                  10_000,
                  requestMessage.sessionAlias,
                  Direction.FIRST
               ) { //Receiving one message
                  passOn("ExecutionReport") { //The type of message success ExecutionReport
                     fieldsMap["ClOrdID"] == requestMessage.fieldsMap["ClOrdID"] //ClOrdID of response message should be equal to this field in request message
                  }
                  failOn("BusinessMessageReject") { //The type of message success ExecutionReport
                     fieldsMap["BusinessRejectRefID"] == requestMessage.fieldsMap["ClOrdID"] //ClOrdID of response message should be equal to this field in request message
                  }
               }
      
               val placeMessageResponse: PlaceMessageResponse = //Create PlaceMessageResponse object
                  PlaceMessageResponse.newBuilder()
                     .setResponseMessage(receiveMessage)
                     .setStatus(RequestStatus.newBuilder().setStatus(RequestStatus.Status.SUCCESS))
                     .setCheckpointId(checkpoint)
                     .build()
      
               emitResult(placeMessageResponse) //Transmitting the created placeMessageResponse StreamObserver
            }
      }
   ```
   
### Action Methods
- sends message to connector
   ```kotlin
   fun send(
        message: Message, //message to send
        sessionAlias: String = message.sessionAlias, //session alias for sending a message
        timeout: Long = 1_000L, //timeout, which is specified when waiting for an echo message
        waitEcho: Boolean = false, //'true' if you need to return an echo message
        cleanBuffer: Boolean = true, //'true' if you need to clear the message cache
        description: String = "" //event description
   ): Message
   ```
- returns the first message matching the filter
  ```kotlin
   fun receive(
        timeout: Long, //message waiting time
        sessionAlias: String, //session alias for receiving the message
        direction: Direction = Direction.SECOND, //direction to receive the message
        description: String = "", //event description
        filter: IReceiveBuilder.() -> Unit //filter for message selection
   ): Message
   ```
- returns all messages matching the filter
  ```kotlin
   fun repeat(
        func: () -> Message //a function that returns a message
   ): () -> Message 
  
   infix fun (() -> Message).until(
        until: (Message) -> Boolean //the filter, if it returns `true`, the function repeats
   ): List<Message>
   ```
- passes the value to the stream
  ```kotlin
   fun emitResult(
        result: T //value passed to the stream
   )
   ```
- creates a checkpoint and returns it
  ```kotlin
   fun registerCheckPoint(
        parentEventId: EventID?, //parent EventID
        description: String //event description
  ): Checkpoint
   ```