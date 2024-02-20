package bgu.ds.worker.config;

import java.util.List;

public interface WorkerAWSConfig {
    String sqsWorkersInputQueueName();
    String sqsWorkersOutputQueueName();
    String workersKeepAliveQueueName();
    List<String> entityTypes();
    int sarcasmThreshold();
    int processingThreads();
    int keepAliveIntervalSeconds();
    int consumerVisibilityTimeout();
    int consumerMaxVisibilityExtensionTime();
    int consumerVisibilityThreadSleepTime();
    int consumerMaxMessagesPerPoll();
    int consumerMaxMessagesInFlight();
}