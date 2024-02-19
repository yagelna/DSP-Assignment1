package bgu.ds.worker;

import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.worker.config.AWSConfigProvider;
import bgu.ds.worker.config.WorkerAWSConfig;
import bgu.ds.worker.processors.SqsReviewProcessMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker {
    final static WorkerAWSConfig config = AWSConfigProvider.getConfig();
    final static SqsOperations sqs = SqsOperations.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    private static final Worker instance = new Worker();

    private SqsMessageConsumer consumer;

    private Worker() {}

    public static Worker getInstance() {
        return instance;
    }

    private void setup() {
        sqs.createQueueIfNotExists(config.sqsWorkersInputQueueName());
        sqs.createQueueIfNotExists(config.sqsWorkersOutputQueueName());
    }

    public void start() {
        setup();

        this.consumer = new SqsMessageConsumer(config.sqsWorkersInputQueueName(), 2, 30, 10, 5, 10);
        this.consumer.registerProcessor(SqsMessageType.REVIEW_PROCESS, new SqsReviewProcessMessageProcessor());
        consumer.start();

        try {
            consumer.join();
        } catch (InterruptedException e) {
            logger.info("Worker was interrupted while waiting for consumer thread", e);
        }
    }
}
