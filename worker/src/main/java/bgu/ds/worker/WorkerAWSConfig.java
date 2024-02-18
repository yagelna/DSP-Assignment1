package bgu.ds.worker;

import java.util.List;

public interface WorkerAWSConfig {
    String sqsWorkersInputQueueName();
    String sqsWorkersOutputQueueName();
    List<String> entityTypes();
    int sarcasmThreshold();
}