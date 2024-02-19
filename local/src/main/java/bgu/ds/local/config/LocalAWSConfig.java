package bgu.ds.local.config;

import java.util.List;

public interface LocalAWSConfig {
    String bucketName();
    String sqsInputQueueName();
    String sqsOutputQueuePrefix();
    String ec2Name();
    String instanceType();
    String ami();
    String instanceProfileName();
    String securityGroupId();
    List<String> userDataCommands();
}