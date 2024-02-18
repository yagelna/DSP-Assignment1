package bgu.ds.worker;

import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.*;

import java.util.List;

public class SqsReviewProcessMessageProcessor implements SqsMessageProcessor {
    final static WorkerAWSConfig config = AWSConfigProvider.getConfig();
    final static SqsOperations sqs = SqsOperations.getInstance();
    private final SentimentAnalysisHandler sentimentAnalysisHandler = new SentimentAnalysisHandler();
    private final NamedEntityRecognitionHandler namedEntityRecognitionHandler = new NamedEntityRecognitionHandler();
    private final String queueUrl = sqs.getQueueUrl(config.sqsWorkersOutputQueueName());

    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.REVIEW_PROCESS) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        ReviewProcessMessage reviewProcessMessage = (ReviewProcessMessage) message;
        String fullText = reviewProcessMessage.getTitle() + "/n" + reviewProcessMessage.getText();
        int sentiment = sentimentAnalysisHandler.findSentiment(fullText);
        List<String> entities = namedEntityRecognitionHandler.getEntities(fullText, config.entityTypes());
        boolean sarcasm = Math.abs(reviewProcessMessage.getRating() - sentiment) > config.sarcasmThreshold();
        ReviewCompleteMessage reviewCompleteMessage = new ReviewCompleteMessage(reviewProcessMessage.getId(),
                new ReviewResult(reviewProcessMessage.getLink(), sentiment, sarcasm, entities));
        sqs.sendMessage(queueUrl, reviewCompleteMessage);
    }
}
