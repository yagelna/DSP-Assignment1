package bgu.ds.local.config;

import java.util.List;

public interface LocalAWSConfig {
    String bucketName();
    String sqsInputQueueName();
    String sqsOutputQueuePrefix();
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