package com.RNLayerKit.modules;


import android.annotation.SuppressLint;

import com.RNLayerKit.listeners.AuthenticationListener;
import com.RNLayerKit.listeners.ChangeEventListener;
import com.RNLayerKit.utils.ConverterHelper;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.IllegalViewOperationException;
import com.layer.sdk.LayerClient;
import com.layer.sdk.exceptions.LayerConversationException;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.Identity;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.Message.RecipientStatus;
import com.layer.sdk.messaging.MessageOptions;
import com.layer.sdk.messaging.MessagePart;
import com.layer.sdk.messaging.PushNotificationPayload;
import com.layer.sdk.query.Predicate;
import com.layer.sdk.query.Query;
import com.layer.sdk.query.Query.Builder;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nullable;


public class RNLayerModule extends ReactContextBaseJavaModule {

    // Class Variables
    private final static String YES = "YES";
    private final static String ZERO = "0";

    public static String userIDGlobal;
    public static String headerGlobal;
    public static Identity userIdentityGlobal;

    // Class intaces
    private ReactApplicationContext reactContext;
    private LayerClient layerClient;

    private AuthenticationListener authenticationListener;
    private ChangeEventListener changeEventListener;

    public RNLayerModule(ReactApplicationContext reactContext, LayerClient layerClient) {
        super(reactContext);
        this.reactContext = reactContext;
        this.layerClient = layerClient;

    }

    /* ***************************************************************************** */
    /*                                                                               */
    /* OVERRIDE METHODS                                                              */
    /*                                                                               */
    /* ***************************************************************************** */

    @Override
    public String getName() {
        return "RNLayerKit";
    }

    public void sendEvent(ReactContext reactContext,
                          String eventName,
                          @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    /* ***************************************************************************** */
    /*                                                                               */
    /* REACT METHODS                                                                 */
    /*                                                                               */
    /* ***************************************************************************** */

    @ReactMethod
    @SuppressWarnings("unused")
    public void connect(
            String appIDstr,
            String deviceToken,
            Promise promise) {

        try {
            LayerClient.Options options = new LayerClient.Options();
            options.historicSyncPolicy(LayerClient.Options.HistoricSyncPolicy.ALL_MESSAGES);
            options.useFirebaseCloudMessaging(true);
            layerClient = LayerClient.newInstance(this.reactContext, appIDstr, options);

            if (authenticationListener == null) {
                authenticationListener = new AuthenticationListener();
            }
            layerClient.registerAuthenticationListener(authenticationListener);

            if (changeEventListener == null) {
                changeEventListener = new ChangeEventListener( this );
            }
            layerClient.registerEventListener(changeEventListener);

            layerClient.connect();

            promise.resolve( YES );

        } catch (IllegalViewOperationException e) {
            promise.reject(e);
        }

    }

    @ReactMethod
    @SuppressWarnings("unused")
    public void disconnect() {

        if (layerClient != null) {
            if (layerClient.isConnected()) {
                layerClient.deauthenticate();
            }
        }

    }


    @ReactMethod
    @SuppressWarnings("unused")
    public void authenticateLayerWithUserID(
            String userID,
            String header,
            Promise promise) {

        try {

            userIDGlobal = userID;
            headerGlobal = header;
            layerClient.authenticate();

            String count;
            count = getMessagesCount();

            WritableArray writableArray = new WritableNativeArray();
            writableArray.pushString(YES);
            writableArray.pushInt(Integer.parseInt(count));

            promise.resolve(writableArray);
        } catch (IllegalViewOperationException e) {
            promise.reject(e);
        }

    }

    @SuppressWarnings("unchecked")
    private String getMessagesCount() {

        try {

            Query query = Query.builder(Message.class)
                    .predicate(new Predicate(Message.Property.IS_UNREAD, Predicate
                            .Operator.EQUAL_TO, true))
                    .build();

            List results = layerClient.executeQuery(query, Query.ResultType.COUNT);

            if (results != null && results.size() > 0) {
                return String.valueOf(results.get(0));
            }

        } catch (IllegalViewOperationException ignored) {
        }

        return ZERO;

    }

    @ReactMethod
    @SuppressWarnings({"unchecked", "UnusedParameters", "unused"})
    public void getConversations(
            int limit,
            int offset,
            Promise promise) {

        try {
            WritableArray writableArray = new WritableNativeArray();

            Builder builder = Query.builder(Conversation.class);

            if (limit != 0) {
                builder.limit(limit);
            }

            Query query = builder.build();

            List results = layerClient.executeQuery(query, Query.ResultType.OBJECTS);

            if (results != null) {
                writableArray.pushString(YES);
                writableArray.pushArray(ConverterHelper.conversationsToWritableArray(results));
                promise.resolve(writableArray);
            } else {
                promise.reject( new Throwable("Error creating conversations") );
            }

        } catch (IllegalViewOperationException e) {
            promise.reject(e);
        }

    }

    @ReactMethod
    @SuppressWarnings({"unchecked", "unused"})
    public void markAllAsRead (
            String convoID,
            Promise promise) {

        try {
            Builder builder = Query.builder(Message.class);

            Conversation conversation = fetchConvoWithId(convoID, layerClient);

            if (conversation != null) {

                builder.predicate(new Predicate(Message.Property.CONVERSATION, Predicate
                        .Operator.EQUAL_TO, conversation));

                Query query = builder.build();

                List messages = layerClient.executeQuery(query, Query.ResultType.OBJECTS);
                if (messages != null) {

                    for (int i = 0; i < messages.size(); i++) {
                        Message message = (Message) messages.get(i);
                        message.markAsRead();
                    }

                    WritableArray writableArray = new WritableNativeArray();
                    String count = getMessagesCount();
                    writableArray.pushInt(Integer.parseInt(count));
                    writableArray.pushString(YES);
                    promise.resolve(writableArray);

                } else {
                    promise.reject(new Throwable("Error getting conversations"));
                }

            } else  {
                promise.reject(new Throwable("Error getting conversation from convo id"));
            }
        } catch (IllegalViewOperationException e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    @SuppressWarnings({"unchecked", "UnusedParameters", "unused"})
    public void getMessages(
            String convoID,
            ReadableArray userIDs,
            int limit,
            int offset,
            Promise promise) {

        WritableArray writableArray = new WritableNativeArray();

        if (convoID != null) {
            try {
                Builder builder = Query.builder(Message.class);
                builder.predicate(new Predicate(Message.Property.CONVERSATION, Predicate
                        .Operator.EQUAL_TO, this.fetchConvoWithId(convoID, layerClient)));
                if (limit != 0) {
                    builder.limit(limit);
                }
                Query query = builder.build();
                List<Message> results = layerClient.executeQuery(query, Query.ResultType.OBJECTS);
                if (results != null) {
                    writableArray.pushString(YES);
                    writableArray.pushArray(ConverterHelper.messagesToWritableArray(results));
                    promise.resolve(writableArray);
                }
            } catch (IllegalViewOperationException e) {
                promise.reject(e);
            }
        } else {
            Conversation conversation = fetchLayerConversationWithParticipants(userIDs, layerClient);
            try {
                Builder builder = Query.builder(Message.class);
                builder.predicate(new Predicate(Message.Property.CONVERSATION, Predicate
                        .Operator.EQUAL_TO, conversation));
                if (limit != 0)
                    builder.limit(limit);
                Query query = builder.build();
                List<Message> results = layerClient.executeQuery(query, Query.ResultType.OBJECTS);
                if (results != null) {
                    writableArray.pushString(YES);
                    writableArray.pushArray(ConverterHelper.messagesToWritableArray(results));
                    writableArray.pushString(conversation.getId().toString());
                    promise.resolve(writableArray);
                }
            } catch (IllegalViewOperationException e) {
                promise.reject(e);
            }
        }
    }

    @ReactMethod
    @SuppressWarnings({"unchecked", "unused"})
    public void sendMessageToUserIDs(
            String messageText,
            ReadableArray userIDs,
            Promise promise) {

        try {

            if (!layerClient.isConnected()) {
                layerClient.connect();
            }

            Conversation conversation = fetchLayerConversationWithParticipants(userIDs, layerClient);

            MessagePart messagePart = layerClient.newMessagePart(messageText);

            Map<String, String> data = new HashMap();
            data.put("user_id", userIDGlobal);

            MessageOptions options = new MessageOptions();
            PushNotificationPayload payload = new PushNotificationPayload.Builder()
                    .text(messageText)
                    .title("New Message")
                    .data(data)
                    .build();
            options.defaultPushNotificationPayload(payload);

            Message message = layerClient.newMessage(options, Collections.singletonList(messagePart));

            conversation.send(message);
            promise.resolve(YES);

        } catch (IllegalViewOperationException e) {
            promise.reject(e);
        }

    }

    /* ***************************************************************************** */
    /*                                                                               */
    /* BUSINESS METHODS                                                              */
    /*                                                                               */
    /* ***************************************************************************** */

    @SuppressWarnings("unchecked")
    private Conversation fetchLayerConversationWithParticipants(
            ReadableArray userIDs,
            LayerClient client) {

        String[] userIDsArray = new String[userIDs.size()];

        for (int i = 0; i < userIDs.size(); i++) {
            userIDsArray[i] = userIDs.getString(i);
        }

        Query query = Query.builder(Conversation.class)
                .predicate(new Predicate(Conversation.Property.PARTICIPANTS, Predicate.Operator.EQUAL_TO, userIDsArray))
                .build();

        List results = client.executeQuery(query, Query.ResultType.OBJECTS);

        if (results != null && results.size() > 0) {
            return (Conversation) results.get(0);
        }

        Conversation conversation;
        try {
            // Try creating a new distinct conversation with the given user
            conversation = layerClient.newConversationWithUserIds(userIDsArray);
        } catch (LayerConversationException e) {
            // If a distinct conversation with the given user already exists, use that one instead
            conversation = e.getConversation();
        }
        return conversation;

    }

    @SuppressWarnings("unchecked")
    private Conversation fetchConvoWithId(
            String convoID,
            LayerClient client) {

        Query query = Query.builder(Conversation.class)
                .predicate(new Predicate(Conversation.Property.ID, Predicate.Operator.EQUAL_TO, convoID))
                .build();

        List<Conversation> results = client.executeQuery(query, Query.ResultType.OBJECTS);
        if (results != null) {
            if (results.size() > 0) {
                return results.get(0);
            }
        }

        return null;
    }



    public ReactApplicationContext getReactContext() {
        return reactContext;
    }
}
