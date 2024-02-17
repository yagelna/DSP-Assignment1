package bgu.ds.manager;

import java.util.List;

public interface ManagerAWSConfig {
    String sqsTasksInputQueueName();
    String sqsWorkersInputQueueName();
    String sqsWorkersOutputQueueName();
    String ec2Name();
    String instanceType();
    String ami();
    String instanceProfileName();
    String securityGroupId();
    List<String> userDataCommands();
}