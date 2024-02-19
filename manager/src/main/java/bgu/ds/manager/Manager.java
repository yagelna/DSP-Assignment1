package bgu.ds.manager;

import bgu.ds.common.awssdk.Ec2Operations;
import bgu.ds.common.awssdk.S3ObjectOperations;
import bgu.ds.common.sqs.protocol.ReviewProcessMessage;
import bgu.ds.common.sqs.protocol.ReviewResult;
import bgu.ds.common.sqs.protocol.SendOutputMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import bgu.ds.manager.config.AWSConfigProvider;
import bgu.ds.manager.config.ManagerAWSConfig;
import bgu.ds.manager.handlers.WorkersHandler;
import bgu.ds.manager.models.ProductReview;
import bgu.ds.manager.models.Review;
import bgu.ds.manager.processors.SqsInputMessageProcessor;
import bgu.ds.manager.processors.SqsReviewCompleteMessageProcessor;
import bgu.ds.manager.processors.SqsSetWorkersCountMessageProcessor;
import bgu.ds.manager.processors.SqsTerminateManagerMessageProcessor;
import com.google.gson.Gson;
import com.google.gson.JsonStreamParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Manager {
    final static ManagerAWSConfig config = AWSConfigProvider.getConfig();
    final static SqsOperations sqs = SqsOperations.getInstance();
    final static S3ObjectOperations s3 = S3ObjectOperations.getInstance();
    final static Ec2Operations ec2 = Ec2Operations.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(Manager.class);

    private static final Manager instance = new Manager();

    private SqsMessageConsumer inputConsumer;
    private SqsMessageConsumer outputConsumer;
    private WorkersHandler workersHandler;
    private final Map<String, UUID> pendingReviews = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ReviewResult>> completedReviews = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> reviewsCounter = new ConcurrentHashMap<>();
    private final Map<UUID, String> inputUUIDToOutputQueueName = new ConcurrentHashMap<>();
    private volatile boolean terminate = false;

    private Manager() {
    }

    public static Manager getInstance() {
        return instance;
    }

    public boolean setTasksPerWorker(int tasksPerWorker) {
        if (workersHandler != null) {
            workersHandler.setTasksPerWorker(tasksPerWorker);
            return true;
        }
        return false;
    }

    public void addInputFile(UUID inputId, String inputBucketName, String objectKey, String outputQueueName)
            throws IOException {
        File tempFile = s3.getObject(inputBucketName, objectKey);
        inputUUIDToOutputQueueName.put(inputId, outputQueueName);
        Gson gson = new Gson();
        JsonStreamParser parser = new JsonStreamParser(new FileReader(tempFile));
        while (parser.hasNext()) {
            ProductReview productReview = gson.fromJson(parser.next(), ProductReview.class);
            // We first want to add the reviews to the pending reviews map,
            // and only then send the reviews to the workers.
            for (Review review : productReview.getReviews()) {
                addPendingReview(inputId, review.getId());
            }

            sqs.sendBatchMessages(sqs.getQueueUrl(config.sqsWorkersInputQueueName()),
                    Arrays.stream(productReview.getReviews())
                            .map(review ->
                                    new ReviewProcessMessage(review.getId(), review.getLink(), review.getTitle(),
                                            review.getText(), review.getRating()))
                            .toList());
        }
        tempFile.delete();
    }

    private void addPendingReview(UUID inputId, String reviewId) {
        if (pendingReviews.putIfAbsent(reviewId, inputId) == null) {
            logger.debug("Review {} is pending", reviewId);
            if (reviewsCounter.putIfAbsent(inputId, new AtomicInteger(1)) != null) {
                reviewsCounter.get(inputId).getAndIncrement();
            }
        } else {
            logger.debug("Review {} is already pending", reviewId);
        }
    }

    public int getPendingReviewsCount() {
        return pendingReviews.size();
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

        int counter = reviewsCounter.get(inputId).decrementAndGet();
        if (counter == 0) {
            logger.info("All reviews for input {} are completed", inputId);
            reviewsCounter.remove(inputId);
            completeInputFile(inputId);
        }
    }

    private void completeInputFile(UUID inputId) {
        // Create html, upload to s3 and send message to local app.
        try {
            File tempFile = File.createTempFile("output-", ".html");
            tempFile.deleteOnExit();
            FileWriter fileWriter = new FileWriter(tempFile, true);
            fileWriter.write(createHtmlContent(inputId));
            fileWriter.close();

            String objectKey = s3.putObject(tempFile.getAbsolutePath(), config.bucketName());
            sqs.sendMessage(inputUUIDToOutputQueueName.remove(inputId), new SendOutputMessage(inputId, config.bucketName(),
                    objectKey));
        } catch (IOException e) {
            logger.error("Failed to write output file", e);
        }

        // If there are no more pending reviews and the manager is set to terminate, terminate.
        if (pendingReviews.isEmpty() && terminate) {
            terminate();
        }
    }

    private String createHtmlContent(UUID inputId) {
        // [0] -> very negetive color ... [4] -> very positive
        String colors[] = {"darkred", "red", "black", "lightgreen", "darkgreen"};

        String HTMLString = "<html>\n" +
                "<head>\n" +
                "<title>Sarcasm Analysis</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "$body" +
                "</body>\n" +
                "</html>";
        String productTitle = "Product Id: " + inputId;
        StringBuilder htmlBody = new StringBuilder("<h2>"+ productTitle +"</h2>\n<ul>\n");
        for (ReviewResult reviewResult : completedReviews.get(inputId).values()) {
            htmlBody.append(String.format(
                    "<li><a href=\"%s\" style=\"color:%s;\">%s</a>" +
                            "<p>Entities: %s</p><p>Sarcasm detected? <b>%b</b></p></li>\n"
            , reviewResult.getLink(), colors[reviewResult.getSentiment()], reviewResult.getLink(),
                    String.join(", ",reviewResult.getEntities()), reviewResult.getSarcasm()));
        }
        htmlBody.append("</ul>\n");
        return HTMLString.replace("$body", htmlBody.toString());
    }

    public void shutdown() {
        logger.info("Shutting down manager");
        // If the manager is not set to terminate, set it to terminate and wait for all pending reviews to complete.
        // Shutdown input consumer to stop receiving new reviews.
        if (inputConsumer != null)
            inputConsumer.shutdown();
        terminate = true;
        // If there are no pending reviews, terminate immediately.
        if (pendingReviews.isEmpty()) {
            terminate();
        }
    }

    private void terminate() {
        if (outputConsumer != null)
            outputConsumer.shutdown();
        if (workersHandler != null)
            workersHandler.shutdown();
    }

    private void setup() {
        s3.createBucketIfNotExists(config.bucketName());
        sqs.createQueueIfNotExists(config.sqsTasksInputQueueName());
        sqs.createQueueIfNotExists(config.sqsWorkersInputQueueName());
        sqs.createQueueIfNotExists(config.sqsWorkersOutputQueueName());
    }

    public void start() {
        setup();

        this.workersHandler = new WorkersHandler(config.minWorkersCount(), config.maxWorkersCount(),
                config.workersHandlerThreadSleepTime());
        workersHandler.start();

        String inputQueueUrl = sqs.getQueueUrl(config.sqsTasksInputQueueName());
        this.inputConsumer = new SqsMessageConsumer(inputQueueUrl, config.consumerThreads(),
                config.consumerVisibilityTimeout(), config.consumerVisibilityThreadSleepTime());
        inputConsumer.registerProcessor(SqsMessageType.ADD_INPUT, new SqsInputMessageProcessor());
        inputConsumer.registerProcessor(SqsMessageType.SET_WORKERS_COUNT, new SqsSetWorkersCountMessageProcessor());
        inputConsumer.registerProcessor(SqsMessageType.TERMINATE_MANAGER, new SqsTerminateManagerMessageProcessor());
        inputConsumer.start();

        String outputQueueUrl = sqs.getQueueUrl(config.sqsWorkersOutputQueueName());
        this.outputConsumer = new SqsMessageConsumer(outputQueueUrl, config.consumerThreads(),
                config.consumerVisibilityTimeout(), config.consumerVisibilityThreadSleepTime());
        outputConsumer.registerProcessor(SqsMessageType.REVIEW_COMPLETE, new SqsReviewCompleteMessageProcessor());
        outputConsumer.start();

        try {
            inputConsumer.join();
            outputConsumer.join();
            workersHandler.join();
        } catch (InterruptedException e) {
            logger.info("Manager was interrupted while waiting for consumer threads and workers handler thread", e);
        }

        ec2.terminateAllInstances(config.managerName());
    }
}
