package bgu.ds.common.sqs.protocol;

import com.google.gson.annotations.SerializedName;

public enum SqsMessageType {
    @SerializedName("0")
    ADD_INPUT(0, AddInputMessage.class),

    @SerializedName("1")
    SEND_OUTPUT(1, SendOutputMessage.class),

    @SerializedName("2")
    SET_WORKERS_COUNT(2, SetWorkersCountMessage.class),

    @SerializedName("3")
    REVIEW_PROCESS(3, ReviewProcessMessage.class),

    @SerializedName("4")
    REVIEW_COMPLETE(4, ReviewCompleteMessage.class),

    @SerializedName("5")
    TERMINATE_MANAGER(5, TerminateManagerMessage.class);

    private final int value;
    private final transient Class<? extends SqsMessage> messageClass;

    SqsMessageType(int value, Class<? extends SqsMessage> messageClass) {
        this.value = value;
        this.messageClass = messageClass;
    }

    public int getValue() {
        return value;
    }

    public Class<? extends SqsMessage> getMessageClass() {
        return messageClass;
    }

    public static SqsMessageType fromInt(int value) {
        for (SqsMessageType type : SqsMessageType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid message type value: " + value);
    }
}
