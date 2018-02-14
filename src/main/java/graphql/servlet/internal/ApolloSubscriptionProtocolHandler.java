package graphql.servlet.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import graphql.ExecutionResult;
import graphql.servlet.GraphQLObjectMapper;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Andrew Potter
 */
public class ApolloSubscriptionProtocolHandler implements SubscriptionProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(ApolloSubscriptionProtocolHandler.class);

    private final SubscriptionHandlerInput input;

    public ApolloSubscriptionProtocolHandler(SubscriptionHandlerInput subscriptionHandlerInput) {
        this.input = subscriptionHandlerInput;
    }

    @Override
    public void onMessage(HandshakeRequest request, Session session, String text) {
        OperationMessage message;
        try {
            message = input.getGraphQLObjectMapper().getJacksonMapper().readValue(text, OperationMessage.class);
        } catch(Throwable t) {
            log.warn("Error parsing message", t);
            sendMessage(session, OperationMessage.Type.GQL_CONNECTION_ERROR, null);
            return;
        }

        switch(message.getType()) {
            case GQL_CONNECTION_INIT:
                sendMessage(session, OperationMessage.Type.GQL_CONNECTION_ACK, message.getId());
//                sendMessage(session, OperationMessage.Type.GQL_CONNECTION_KEEP_ALIVE, message.getId());
                break;

            case GQL_START:
                handleSubscriptionStart(
                    session,
                    message.id,
                    input.getQueryInvoker().query(input.getInvocationInputFactory().create(
                        input.getGraphQLObjectMapper().getJacksonMapper().convertValue(message.payload, GraphQLRequest.class)
                    ))
                );
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSubscriptionStart(Session session, String id, ExecutionResult executionResult) {
        executionResult = input.getGraphQLObjectMapper().sanitizeErrors(executionResult);
        OperationMessage.Type type = input.getGraphQLObjectMapper().areErrorsPresent(executionResult) ? OperationMessage.Type.GQL_ERROR : OperationMessage.Type.GQL_DATA;

        Object data = executionResult.getData();
        if(data instanceof Publisher) {
            if(type == OperationMessage.Type.GQL_DATA) {
                AtomicReference<Subscription> subscriptionReference = new AtomicReference<>();

                ((Publisher<ExecutionResult>) data).subscribe(new Subscriber<ExecutionResult>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscriptionReference.set(subscription);
                        subscriptionReference.get().request(1);
                    }

                    @Override
                    public void onNext(ExecutionResult executionResult) {
                        subscriptionReference.get().request(1);
                        Map<String, Object> result = new HashMap<>();
                        result.put("data", executionResult.getData());
                        sendMessage(session, OperationMessage.Type.GQL_DATA, id, result);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Subscription error", throwable);
                        sendMessage(session, OperationMessage.Type.GQL_ERROR, id);
                    }

                    @Override
                    public void onComplete() {
                        sendMessage(session, OperationMessage.Type.GQL_COMPLETE, id);
                    }
                });
            }
        }

        sendMessage(session, type, id, input.getGraphQLObjectMapper().convertSanitizedExecutionResult(executionResult));
    }

    private void sendMessage(Session session, OperationMessage.Type type, String id) {
        sendMessage(session, type, id, null);
    }

    private void sendMessage(Session session, OperationMessage.Type type, String id, Object payload) {
        try {
            session.getBasicRemote().sendText(input.getGraphQLObjectMapper().getJacksonMapper().writeValueAsString(
                new OperationMessage(type, id, payload)
            ));
        } catch (IOException e) {
            throw new RuntimeException("Error sending subscription response", e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationMessage {
        private Type type;
        private String id;
        private Object payload;

        public OperationMessage() {
        }

        public OperationMessage(Type type, String id, Object payload) {
            this.type = type;
            this.id = id;
            this.payload = payload;
        }

        public Type getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public Object getPayload() {
            return payload;
        }

        public enum Type {

            // Server Messages
            GQL_CONNECTION_ACK("connection_ack"),
            GQL_CONNECTION_ERROR("connection_error"),
            GQL_CONNECTION_KEEP_ALIVE("ka"),
            GQL_DATA("data"),
            GQL_ERROR("error"),
            GQL_COMPLETE("complete"),

            // Client Messages
            GQL_CONNECTION_INIT("connection_init"),
            GQL_CONNECTION_TERMINATE("connection_terminate"),
            GQL_START("start"),
            GQL_STOP("stop");

            private static final Map<String, Type> reverseLookup = new HashMap<>();

            static {
                for(Type type: Type.values()) {
                    reverseLookup.put(type.getType(), type);
                }
            }

            private final String type;

            Type(String type) {
                this.type = type;
            }

            @JsonCreator
            public static Type findType(String type) {
                return reverseLookup.get(type);
            }

            @JsonValue
            public String getType() {
                return type;
            }
        }
    }

}
