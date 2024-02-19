package bgu.ds.common.sqs;

import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.common.awssdk.SqsOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class SqsMessageConsumer extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SqsMessageConsumer.class);
    private static final SqsOperations sqs = SqsOperations.getInstance();
    // Amazon limits the poll size to 10 messages and the amount of in flight messages to 120,000 per queue
    private static final int MAX_MESSAGES_PER_POLL = 10;
    private static final int MAX_MESSAGES_IN_FLIGHT = 120000;
    private volatile boolean terminate = false;
    private final String queueUrl;
    private final int visibilityTimeout;
    private final int maxMessagesPerPoll;
    private final Semaphore semaphore;
    private final Map<SqsMessageType, SqsMessageProcessor> processors= new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final sqsVisibilityTimeoutExtender visibilityTimeoutExtender;



    public SqsMessageConsumer(String queueUrl, int threadsCount, int visibilityTimeout, int visibilityThreadSleepTime) {
        this(queueUrl, threadsCount, visibilityTimeout, visibilityThreadSleepTime,
                MAX_MESSAGES_PER_POLL, MAX_MESSAGES_IN_FLIGHT);
    }

    public SqsMessageConsumer(String queueUrl, int threadsCount, int visibilityTimeout, int visibilityThreadSleepTime,
                              int maxMessagesPerPoll, int maxMessagesInFlight) {
        if (maxMessagesPerPoll > maxMessagesInFlight) {
            throw new IllegalArgumentException("maxMessagesInFlight must be greater or equal to maxMessagesPerPoll");
        }
        this.queueUrl = queueUrl;
        this.executorService = Executors.newFixedThreadPool(threadsCount);
        this.visibilityTimeout = visibilityTimeout;
        this.visibilityTimeoutExtender = new sqsVisibilityTimeoutExtender(queueUrl, visibilityTimeout,
                visibilityThreadSleepTime);
        this.maxMessagesPerPoll = maxMessagesPerPoll;
        this.semaphore = new Semaphore(maxMessagesInFlight);
    }


    public void registerProcessor(SqsMessageType messageType, SqsMessageProcessor processor) {
        processors.put(messageType, processor);
    }

    public void run() {
        visibilityTimeoutExtender.start();
        while (!terminate) {

            try {
                semaphore.acquire(maxMessagesPerPoll);
            } catch (InterruptedException e) {
                logger.warn("SqsMessageConsumer was interrupted");
            }

            List<Message> messages = sqs.receiveMessages(queueUrl, visibilityTimeout, maxMessagesPerPoll);
            if (messages.size() < maxMessagesPerPoll) {
                semaphore.release(maxMessagesPerPoll - messages.size());
            }

            for (Message message : messages) {
                SqsMessage sqsMessage = SqsMessage.fromJSON(message.body());
                logger.debug("Received message: " + sqsMessage);
                SqsMessageProcessor processor = processors.get(sqsMessage.getMessageType());
                if (processor != null) {
                    visibilityTimeoutExtender.addMessage(message);
                    executorService.execute(() -> {
                        try {
                            logger.info("Starting processing message: {}", sqsMessage);
                            processor.process(sqsMessage);
                            logger.info("Finished processing message successfully: " + sqsMessage);
                            sqs.deleteMessage(queueUrl, message.receiptHandle());
                        } catch (Exception e) {
                            logger.error("Failed to process message: " + sqsMessage, e);
                        } finally {
                            visibilityTimeoutExtender.removeMessage(message);
                            semaphore.release();
                        }
                    });
                }
            }
        }
        logger.info("SqsMessageConsumer stopped receiving new messages");
        try {
            logger.info("SqsMessageConsumer waiting for visibilityTimeoutExtender thread to finish");
            visibilityTimeoutExtender.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("SqsMessageConsumer finished");
    }

    public void shutdown() {
        logger.info("Shutting down SqsMessageConsumer");
        terminate = true;
        executorService.shutdown();
        visibilityTimeoutExtender.shutdown();
    }
}
