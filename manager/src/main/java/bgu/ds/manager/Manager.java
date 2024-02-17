package bgu.ds.manager;

import bgu.ds.common.sqs.protocol.ReviewResult;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Manager {
    final static ManagerAWSConfig config = AWSConfigProvider.getConfig();
    final static SqsOperations sqs = SqsOperations.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(Manager.class);

    private static final Manager instance = new Manager();

    private final SqsMessageConsumer inputConsumer;
    private final SqsMessageConsumer outputConsumer;
    private final Map<String, UUID> pendingReviews = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ReviewResult>> completedReviews = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> reviewsCounter = new ConcurrentHashMap<>();

    private Manager() {
        setup();
        String inputQueueUrl = sqs.getQueueUrl(config.sqsTasksInputQueueName());
        this.inputConsumer = new SqsMessageConsumer(inputQueueUrl, 5, 30, 10);
        inputConsumer.registerProcessor(SqsMessageType.ADD_INPUT, new SqsInputMessageProcessor());

        String outputQueueUrl = sqs.getQueueUrl(config.sqsWorkersOutputQueueName());
        this.outputConsumer = new SqsMessageConsumer(outputQueueUrl, 5, 30, 10);
        outputConsumer.registerProcessor(SqsMessageType.REVIEW_COMPLETE, new SqsReviewCompleteMessageProcessor());
    }

    public static Manager getInstance() {
        return instance;
    }

    public void addPendingReview(UUID inputId, String reviewId) {
        if (pendingReviews.putIfAbsent(reviewId, inputId) == null) {
            logger.debug("Review {} is pending", reviewId);
            if (reviewsCounter.putIfAbsent(inputId, new AtomicInteger(1)) != null) {
                reviewsCounter.get(inputId).getAndIncrement();
            }
        } else {
            logger.debug("Review {} is already pending", reviewId);
        }
    }

    public void completeReview(String reviewId, ReviewResult result) {
        UUID inputId = pendingReviews.remove(reviewId);
        if (inputId == null) {
            logger.debug("Review {} is not pending", reviewId);
            return;
        }

        logger.info("Review {} is completed", reviewId);
        completedReviews.putIfAbsent(inputId, new ConcurrentHashMap<>());
        completedReviews.get(inputId).put(reviewId, result);

        reviewsCounter.get(inputId).getAndDecrement();
        if (reviewsCounter.get(inputId).get() == 0) {
            logger.info("All reviews for input {} are completed", inputId);
            reviewsCounter.remove(inputId);
            completeInputFile(inputId);
        }
    }

    public void completeInputFile(UUID inputId) {
        // Create html, upload to s3 and send message to local app.
    }

    public void shutdown() {
        inputConsumer.shutdown();
        outputConsumer.shutdown();
    }

    private void setup() {
        sqs.createQueueIfNotExists(config.sqsTasksInputQueueName());
        sqs.createQueueIfNotExists(config.sqsWorkersInputQueueName());
        sqs.createQueueIfNotExists(config.sqsWorkersOutputQueueName());
    }

    public void start() {
        inputConsumer.start();
        outputConsumer.start();
        try {
            inputConsumer.join();
            outputConsumer.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
