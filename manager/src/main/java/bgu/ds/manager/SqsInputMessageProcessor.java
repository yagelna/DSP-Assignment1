package bgu.ds.manager;

import bgu.ds.common.awssdk.S3ObjectOperations;
import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.AddInputMessage;
import bgu.ds.common.sqs.protocol.ReviewProcessMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import com.google.gson.Gson;
import com.google.gson.JsonStreamParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class SqsInputMessageProcessor implements SqsMessageProcessor {
    private final static S3ObjectOperations s3 = S3ObjectOperations.getInstance();
    private final static SqsOperations sqs = SqsOperations.getInstance();
    private final static ManagerAWSConfig config = AWSConfigProvider.getConfig();

    @Override
    public void process(SqsMessage message) {
        try {
            if (message.getMessageType() != SqsMessageType.ADD_INPUT) {
                throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
            }
            AddInputMessage addInputMessage = (AddInputMessage) message;
            File tempFile = s3.getObject(addInputMessage.getBucketName(), addInputMessage.getObjectKey());
            Gson gson = new Gson();
            JsonStreamParser parser = new JsonStreamParser(new FileReader(tempFile));
            while (parser.hasNext()) {
                ProductReview productReview = gson.fromJson(parser.next(), ProductReview.class);
                // We first want to add the reviews to the pending reviews map,
                // and only then send the reviews to the workers.
                for (Review review : productReview.getReviews()) {
                    Manager.getInstance().addPendingReview(addInputMessage.getInputId(), review.getId());
                }

//                for (Review review : productReview.getReviews()) {
//                    sqs.sendMessage(sqs.getQueueUrl(config.sqsWorkersInputQueueName()),
//                            new ReviewProcessMessage(review.getId(), review.getTitle(), review.getText()));
//                }

                // TODO: Send in batch
                sqs.sendBatchMessages(sqs.getQueueUrl(config.sqsWorkersInputQueueName()),
                        Arrays.stream(productReview.getReviews())
                                .map(review ->
                                        new ReviewProcessMessage(review.getId(), review.getTitle(), review.getText()))
                                .toList());
            }
            tempFile.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
