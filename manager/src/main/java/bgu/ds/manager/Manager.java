package bgu.ds.manager;

import bgu.ds.common.awssdk.Ec2Operations;
import bgu.ds.common.awssdk.S3ObjectOperations;
import bgu.ds.common.sqs.protocol.ReviewResult;
import bgu.ds.common.sqs.protocol.SendOutputMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
            sqs.sendMessage(config.sqsOutputQueueName(), new SendOutputMessage(inputId, config.bucketName(),
                    objectKey));
        } catch (IOException e) {
            logger.error("Failed to write output file", e);
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
        if (inputConsumer != null)
            inputConsumer.shutdown();
        if (outputConsumer != null)
            outputConsumer.shutdown();
        if (workersHandler != null)
            workersHandler.shutdown();
    }

    private void setup() {
        sqs.createQueueIfNotExists(config.sqsOutputQueueName());
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
        this.inputConsumer = new SqsMessageConsumer(inputQueueUrl, 5, 30, 10);
        inputConsumer.registerProcessor(SqsMessageType.ADD_INPUT, new SqsInputMessageProcessor());
        inputConsumer.registerProcessor(SqsMessageType.SET_WORKERS_COUNT, new SqsSetWorkersCountMessageProcessor());
        inputConsumer.registerProcessor(SqsMessageType.TERMINATE_MANAGER, new SqsTerminateManagerMessageProcessor());
        inputConsumer.start();

        String outputQueueUrl = sqs.getQueueUrl(config.sqsWorkersOutputQueueName());
        this.outputConsumer = new SqsMessageConsumer(outputQueueUrl, 5, 30, 10);
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
