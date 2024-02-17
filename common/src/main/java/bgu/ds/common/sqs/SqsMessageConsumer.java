package bgu.ds.common.sqs;

import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.common.awssdk.SqsOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SqsMessageConsumer extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SqsMessageConsumer.class);
    private static final SqsOperations sqs = SqsOperations.getInstance();
    private volatile boolean terminate = false;
    private final String queueUrl;
    private final int visibilityTimeout;
    private final Map<SqsMessageType, SqsMessageProcessor> processors= new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final sqsVisibilityTimeoutExtender visibilityTimeoutExtender;


    public SqsMessageConsumer(String queueUrl, int threadsCount, int visibilityTimeout, int visibilityThreadSleepTime) {
        this.queueUrl = queueUrl;
        this.executorService = Executors.newFixedThreadPool(threadsCount);
        this.visibilityTimeout = visibilityTimeout;
        this.visibilityTimeoutExtender = new sqsVisibilityTimeoutExtender(queueUrl, visibilityTimeout,
                visibilityThreadSleepTime);
    }

    public void registerProcessor(SqsMessageType messageType, SqsMessageProcessor processor) {
        processors.put(messageType, processor);
    }

    public void run() {
        visibilityTimeoutExtender.start();
        while (!terminate) {
            List<Message> messages = sqs.receiveMessage(queueUrl, visibilityTimeout);
            if (messages != null) {
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
                                logger.info("Finished processing message: " + sqsMessage);
                                sqs.deleteMessage(queueUrl, message.receiptHandle());
                            } catch (Exception e) {
                                logger.error("Failed to process message: " + sqsMessage, e);
                            } finally {
                                visibilityTimeoutExtender.removeMessage(message);
                            }
                        });
                    }
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
