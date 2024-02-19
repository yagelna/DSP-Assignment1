package bgu.ds.manager.config;

import java.util.List;

public interface ManagerAWSConfig {
    String managerName();
    String bucketName();
    String sqsTasksInputQueueName();
    String sqsWorkersInputQueueName();
    String sqsWorkersOutputQueueName();
    int minWorkersCount();
    int maxWorkersCount();
    int workersHandlerThreadSleepTime();
    int consumerThreads();
    int consumerVisibilityTimeout();
    int consumerVisibilityThreadSleepTime();
    String ec2Name();
    String instanceType();
    String ami();
    String instanceProfileName();
    String securityGroupName();
    List<String> userDataCommands();
}