package bgu.ds.common.sqs;

import bgu.ds.common.awssdk.SqsOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class sqsVisibilityTimeoutExtender extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(SqsMessageConsumer.class);
    private final SqsOperations sqs = SqsOperations.getInstance();
    private final String queueUrl;
    private final int visibilityTimeout;
    private final int maxVisibilityExtensionTime;
    private final int threadSleepTime;
    private final Map<Message, Date> inProcessMessages = new ConcurrentHashMap<>();
    private volatile boolean terminate = false;

    public sqsVisibilityTimeoutExtender(String queueUrl, int visibilityTimeout, int maxVisibilityExtensionTime,
                                        int threadSleepTime) {
        this.queueUrl = queueUrl;
        this.visibilityTimeout = visibilityTimeout;
        this.maxVisibilityExtensionTime = maxVisibilityExtensionTime;
        this.threadSleepTime = threadSleepTime;
    }

    public void addMessage(Message message) {
        inProcessMessages.put(message, new Date());
    }

    public void removeMessage(Message message) {
        if (inProcessMessages.remove(message) == null) {
            logger.warn("Message {} was already removed from sqsVisibilityTimeoutExtender", message.messageId());
        }
        if (inProcessMessages.isEmpty() && terminate) {
            this.interrupt();
        }
    }

    public void run() {
        while (!terminate || !inProcessMessages.isEmpty()) {
            logger.info("Extending visibility timeout for {} messages", inProcessMessages.size());
            try {
                inProcessMessages.forEach((message, date) -> {
                    if (new Date().getTime() - date.getTime() > maxVisibilityExtensionTime * 1000L) {
                        logger.warn("Message {} has been in process for too long, skipping visibility timeout extension",
                                message.messageId());
                        removeMessage(message);
                    }
                });
                sqs.extendVisibilityTimeoutBatch(queueUrl,
                        inProcessMessages.keySet().stream().map(Message::receiptHandle).toList(),
                        this.visibilityTimeout);
            } catch (Exception e) {
                logger.warn("Failed to extend visibility timeout: {}", e.getMessage());
                logger.debug("Failed to extend visibility timeout", e);
            }

            try {
                Thread.sleep(this.threadSleepTime * 1000L);
            } catch (InterruptedException e) {
                logger.info("Visibility timeout extender thread was interrupted");
            }
        }
    }

    public void shutdown() {
        terminate = true;
    }
}
