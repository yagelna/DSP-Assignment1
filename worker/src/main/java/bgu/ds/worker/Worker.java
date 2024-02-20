package bgu.ds.worker;

import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import bgu.ds.common.sqs.protocol.ReviewCompleteMessage;
import bgu.ds.common.sqs.protocol.ReviewResult;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.worker.config.AWSConfigProvider;
import bgu.ds.worker.config.WorkerAWSConfig;
import bgu.ds.worker.handlers.KeepAliveHandler;
import bgu.ds.worker.handlers.NamedEntityRecognitionHandler;
import bgu.ds.worker.handlers.SentimentAnalysisHandler;
import bgu.ds.worker.processors.SqsReviewProcessMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Worker {
    final static WorkerAWSConfig config = AWSConfigProvider.getConfig();
    final static SqsOperations sqs = SqsOperations.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    private final SentimentAnalysisHandler sentimentAnalysisHandler = new SentimentAnalysisHandler();
    private final NamedEntityRecognitionHandler namedEntityRecognitionHandler = new NamedEntityRecognitionHandler();
    private final String outputQueueUrl = sqs.getQueueUrl(config.sqsWorkersOutputQueueName());

    private SqsMessageConsumer consumer;

    public Worker() {}

    private void setup() {
        sqs.createQueueIfNotExists(config.sqsWorkersInputQueueName());
        sqs.createQueueIfNotExists(config.sqsWorkersOutputQueueName());
        sqs.createQueueIfNotExists(config.workersKeepAliveQueueName());
    }

    public void processReview(String reviewId, String title, String text, String link, int rating) {
        String fullText = title + "/n" + text;
        int sentiment = sentimentAnalysisHandler.findSentiment(fullText);
        List<String> entities = namedEntityRecognitionHandler.getEntities(fullText, config.entityTypes());
        boolean sarcasm = Math.abs(rating - sentiment) > config.sarcasmThreshold();
        ReviewCompleteMessage reviewCompleteMessage = new ReviewCompleteMessage(reviewId,
                new ReviewResult(link, sentiment, sarcasm, entities));
        sqs.sendMessage(outputQueueUrl, reviewCompleteMessage);
    }

    public void start() {
        setup();

        this.consumer = new SqsMessageConsumer(sqs.getQueueUrl(config.sqsWorkersInputQueueName()), config.processingThreads(),
                config.consumerVisibilityTimeout(), config.consumerVisibilityThreadSleepTime(),
                config.consumerMaxMessagesPerPoll(), config.consumerMaxMessagesInFlight());
        this.consumer.registerProcessor(SqsMessageType.REVIEW_PROCESS, new SqsReviewProcessMessageProcessor(this));
        consumer.start();

        KeepAliveHandler keepAliveHandler = new KeepAliveHandler(sqs.getQueueUrl(config.workersKeepAliveQueueName()),
                config.keepAliveIntervalSeconds());
        keepAliveHandler.start();

        try {
            consumer.join();
        } catch (InterruptedException e) {
            logger.info("Worker was interrupted while waiting for consumer thread", e);
        }

        try {
            keepAliveHandler.terminate();
            keepAliveHandler.join();
        } catch (InterruptedException e) {
            logger.info("Worker was interrupted while waiting for keepAliveHandler thread", e);
        }
    }
}
