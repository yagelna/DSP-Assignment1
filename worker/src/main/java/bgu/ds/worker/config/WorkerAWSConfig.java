package bgu.ds.worker.config;

import java.util.List;

public interface WorkerAWSConfig {
    String sqsWorkersInputQueueName();
    String sqsWorkersOutputQueueName();
    List<String> entityTypes();
    int sarcasmThreshold();
    int processorThreads();
    int consumerVisibilityTimeout();
    int consumerVisibilityThreadSleepTime();
    int consumerMaxMessagesPerPoll();
    int consumerMaxMessagesInFlight();
}