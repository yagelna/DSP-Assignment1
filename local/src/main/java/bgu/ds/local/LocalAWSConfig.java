package bgu.ds.local;

import java.util.List;

public interface LocalAWSConfig {
    String bucketName();
    String sqsInputQueueName();
    String sqsOutputQueueName();
    String ec2Name();
    String instanceType();
    String ami();
    String instanceProfileName();
    String securityGroupId();
    List<String> userDataCommands();
}