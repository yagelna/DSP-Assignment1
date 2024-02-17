package bgu.ds.common.sqs.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public abstract class SqsMessage {
    private final SqsMessageType messageType;

    protected SqsMessage(SqsMessageType messageType) {
        this.messageType = messageType;
    }

    // Method to get the message type
    public SqsMessageType getMessageType() {
        return messageType;
    }

    // Method to serialize the message to JSON
    public String toJSON() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    // Static method to deserialize a JSON string to a message object
    public static SqsMessage fromJSON(String json) {
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        int messageTypeValue = jsonObject.get("messageType").getAsInt();
        SqsMessageType messageType = SqsMessageType.fromInt(messageTypeValue);
        return gson.fromJson(json, messageType.getMessageClass());
    }
}
