package bgu.ds.common.sqs.protocol;

import java.util.UUID;

public class SendOutputMessage extends SqsMessage{
    private UUID inputId;
    private String bucketName;
    private String objectKey;

    public SendOutputMessage(UUID inputId, String bucketName, String objectKey) {
        super(SqsMessageType.SEND_OUTPUT);
        this.inputId = inputId;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
    }

    public UUID getInputId() {
        return inputId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }
}
