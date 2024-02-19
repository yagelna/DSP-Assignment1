package bgu.ds.common.sqs.protocol;

import java.util.UUID;

public class AddInputMessage extends SqsMessage {
    private UUID inputId;
    private String inputBucketName;
    private String objectKey;
    private String outputQueueName;

    public AddInputMessage(UUID inputId, String inputBucketName, String objectKey, String outputQueueName) {
        super(SqsMessageType.ADD_INPUT);
        this.inputId = inputId;
        this.inputBucketName = inputBucketName;
        this.objectKey = objectKey;
        this.outputQueueName = outputQueueName;
    }

    public UUID getInputId() {
        return inputId;
    }

    public String getInputBucketName() {
        return inputBucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOutputQueueName() {
        return outputQueueName;
    }

}
