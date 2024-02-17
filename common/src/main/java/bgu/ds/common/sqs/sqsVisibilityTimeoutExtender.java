package bgu.ds.common.sqs;

import bgu.ds.common.awssdk.SqsOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class sqsVisibilityTimeoutExtender extends Thread{
    private static final Logger logger = LoggerFactory.getLogger(SqsMessageConsumer.class);
    private final SqsOperations sqs = SqsOperations.getInstance();
    private final String queueUrl;
    private int visibilityTimeout;
    private int threadSleepTime;
    private final Set<Message> inProcessMessages = ConcurrentHashMap.newKeySet();
    private volatile boolean terminate = false;

    public sqsVisibilityTimeoutExtender(String queueUrl, int visibilityTimeout, int threadSleepTime) {
        this.queueUrl = queueUrl;
        this.visibilityTimeout = visibilityTimeout;
        this.threadSleepTime = threadSleepTime;
    }

    public void addMessage(Message message) {
        inProcessMessages.add(message);
    }

    public void removeMessage(Message message) {
        inProcessMessages.remove(message);
        if (inProcessMessages.isEmpty() && terminate) {
            this.interrupt();
        }
    }

    public void run() {
        while (!terminate || !inProcessMessages.isEmpty()) {
            for (Message message : inProcessMessages) {
                try {
                    sqs.extendVisibilityTimeout(queueUrl, message.receiptHandle(), this.visibilityTimeout);
                } catch (Exception e) {
                    logger.error("Failed to extend visibility timeout", e);
                }
            }
            try {
                Thread.sleep(this.threadSleepTime);
            } catch (InterruptedException e) {
                logger.info("Visibility timeout extender thread was interrupted");
            }
        }
    }

    public void shutdown() {
        terminate = true;
        this.interrupt();
    }
}
