package bgu.ds.common.awssdk;

import bgu.ds.common.sqs.protocol.SqsMessage;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

public class SqsOperations {
    private final SqsClient sqsClient;
    private static final Region region = Region.US_EAST_1;

    private static final SqsOperations instance = new SqsOperations();

    private SqsOperations() {
        this.sqsClient = SqsClient.builder().region(region).build();
    }

    public static SqsOperations getInstance() {
        return instance;
    }

    public boolean createQueueIfNotExists(String QueueName) {
        try {
            CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(QueueName)
                    .build();
            sqsClient.createQueue(request);
            return true;
        } catch (QueueNameExistsException e) {
            return false;
        }
    }

    public String getQueueUrl(String QueueName) {
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(QueueName)
                .build();
        return sqsClient.getQueueUrl(getQueueRequest).queueUrl();
    }

    public void sendMessage(String queueUrl, SqsMessage message) {
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message.toJSON())
                .build();
        sqsClient.sendMessage(sendMsgRequest);
    }

    public void sendBatchMessages(String queueUrl, List<? extends SqsMessage> messages) {
        List<SendMessageBatchRequestEntry> entries = messages.stream()
                .map(message -> SendMessageBatchRequestEntry.builder()
                        .messageBody(message.toJSON())
                        .build())
                .toList();
        SendMessageBatchRequest sendBatchRequest = SendMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build();
        sqsClient.sendMessageBatch(sendBatchRequest);
    }

    public List<Message> receiveMessage(String queueUrl, int visibilityTimeout) {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .visibilityTimeout(visibilityTimeout)
                .build();
        return sqsClient.receiveMessage(receiveRequest).messages();
    }

    public void deleteMessage(String queueUrl, String receiptHandle) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();
        sqsClient.deleteMessage(deleteRequest);
    }

    public void extendVisibilityTimeout(String queueUrl, String receiptHandle, int visibilityTimeout) {
        ChangeMessageVisibilityRequest changeRequest = ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(visibilityTimeout)
                .build();
        sqsClient.changeMessageVisibility(changeRequest);
    }
}
