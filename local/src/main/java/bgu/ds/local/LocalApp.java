package bgu.ds.local;

import bgu.ds.common.awssdk.Ec2Operations;
import bgu.ds.common.awssdk.S3ObjectOperations;
import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import bgu.ds.common.sqs.protocol.AddInputMessage;
import bgu.ds.common.sqs.protocol.SetWorkersCountMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalApp {
    private static final Logger logger = LoggerFactory.getLogger(LocalApp.class);
    final static S3ObjectOperations s3 = S3ObjectOperations.getInstance();
    final static Ec2Operations ec2 = Ec2Operations.getInstance();
    final static SqsOperations sqs = SqsOperations.getInstance();
    final static LocalAWSConfig config = AWSConfigProvider.getConfig();

    private static final LocalApp instance = new LocalApp();

    private final SqsMessageConsumer consumer;
    private final Map<UUID, String> inputUUIDToOutputPath = new ConcurrentHashMap<>();
    private AtomicInteger inputFilesCount;

    private LocalApp() {
        setup();
        this.consumer = new SqsMessageConsumer(config.sqsOutputQueueName(), 5, 30, 10);
        this.consumer.registerProcessor(SqsMessageType.SEND_OUTPUT, new SqsOutputMessageProcessor());
    }

    public static LocalApp getInstance() {
        return instance;
    }

    private void setup() {
        if (!ec2.isInstanceRunning(config.ec2Name())) {
            ec2.createInstance(config.ec2Name(), config.instanceType(), config.ami(), config.instanceProfileName(),
                    config.securityGroupId(), config.userDataCommands());
        } else {
            System.out.println("[DEBUG] Instance is already running.");
        }

        System.out.println("[DEBUG] Create bucket if not exist.");
        s3.createBucketIfNotExists(config.bucketName());
        sqs.createQueueIfNotExists(config.sqsInputQueueName());
        sqs.createQueueIfNotExists(config.sqsOutputQueueName());
    }

    public void addOutputFile(UUID inputId, String bucketName, String objectKey) {
        String filePath = inputUUIDToOutputPath.remove(inputId);
        if (filePath == null) {
            logger.warn("Input id {} is not found", inputId);
            return;
        }

        try {
            s3.getObject(objectKey, bucketName, new File(filePath));
        } catch (IOException e) {
            logger.error("Failed to write object {} from bucket {} to path {}", objectKey, bucketName, filePath, e);
        }

        if (inputFilesCount.decrementAndGet() == 0) {
            consumer.shutdown();
        }
    }

    public void start(String[] inFilesPath, String[] outFilesPath, int tasksPerWorker, boolean terminate) {
        // Send a message to the Manager to set the number of workers
        String inputQueueUrl = sqs.getQueueUrl(config.sqsInputQueueName());
        sqs.sendMessage(inputQueueUrl, new SetWorkersCountMessage(tasksPerWorker));

        // Set the input files count
        inputFilesCount = new AtomicInteger(inFilesPath.length);

        // Start the consumer
        consumer.start();

        // Send the input files to the Manager
        for (int i =0; i < inFilesPath.length; i++) {
            String objectKey = s3.putObject(inFilesPath[i], config.bucketName());
            UUID uuid = UUID.randomUUID();
            inputUUIDToOutputPath.put(uuid, outFilesPath[i]);
            sqs.sendMessage(inputQueueUrl, new AddInputMessage(uuid, config.bucketName(), objectKey));
        }

        // Wait for all the input files to be processed
        try {
            consumer.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (terminate) {
            logger.info("Terminating Manager instance");
            ec2.terminateInstances(config.ec2Name());
        }
    }
}
