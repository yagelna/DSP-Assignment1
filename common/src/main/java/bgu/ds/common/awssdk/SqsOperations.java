package bgu.ds.common.awssdk;

import bgu.ds.common.sqs.protocol.SqsMessage;
import com.google.common.collect.Lists;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

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
            // Enable long polling when creating a queue.
            HashMap<QueueAttributeName, String> attributes = new HashMap<>();
            attributes.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20");
            CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(QueueName)
                    .attributes(attributes)
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
        Lists.partition(messages, 10).forEach(partition -> {
            SendMessageBatchRequest sendBatchRequest = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(partition.stream()
                            .map(message -> SendMessageBatchRequestEntry.builder()
                                    .id(UUID.randomUUID().toString())
                                    .messageBody(message.toJSON())
                                    .build())
                            .toList())
                    .build();
            sqsClient.sendMessageBatch(sendBatchRequest);
        });
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

    public void extendVisibilityTimeoutBatch(String queueUrl, List<String> receiptHandles, int visibilityTimeout) {
        Lists.partition(receiptHandles, 10).forEach(partition -> {
            ChangeMessageVisibilityBatchRequest sendBatchRequest = ChangeMessageVisibilityBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(partition.stream()
                            .map(receiptHandle -> ChangeMessageVisibilityBatchRequestEntry.builder()
                                    .id(UUID.randomUUID().toString())
                                    .receiptHandle(receiptHandle)
                                    .visibilityTimeout(visibilityTimeout)
                                    .build())
                            .toList())
                    .build();
            sqsClient.changeMessageVisibilityBatch(sendBatchRequest);
        });
    }
}
