package bgu.ds.common.awssdk;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Ec2Operations {
    private final Ec2Client ec2Client;
    private static final Region region = Region.US_EAST_1;

    private static final Ec2Operations instance = new Ec2Operations();

    private Ec2Operations() {
        ec2Client = Ec2Client.builder().region(region).build();
    }

    public static Ec2Operations getInstance() {
        return instance;
    }

    public List<String> createInstance(String name, String instanceType, String ami, int minCount, int maxCount,
                                         String instanceProfileName, String securityGroupName,
                                         List<String> userDataCommands) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.fromValue(instanceType))
                .imageId(ami)
                .minCount(minCount)
                .maxCount(maxCount)
                .userData(getEC2userData(userDataCommands))
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name(instanceProfileName).build())
                .securityGroups(securityGroupName)
                .build();
        RunInstancesResponse response = ec2Client.runInstances(runRequest);

        software.amazon.awssdk.services.ec2.model.Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(response.instances().stream().map(Instance::instanceId).toList())
                .tags(tag)
                .build();

        ec2Client.createTags(tagRequest);
        return response.instances().stream().map(Instance::instanceId).toList();
    }

    public boolean isInstanceRunning(String name) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(Filter.builder().name("tag:Name").values(name).build())
                .build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        return response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .anyMatch(instance -> instance.state().name().equals(InstanceStateName.RUNNING) ||
                        instance.state().name().equals(InstanceStateName.PENDING));
    }

    public List<String> getRunningInstancesIds(String name) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(Filter.builder().name("tag:Name").values(name).build())
                .build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        return response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.state().name().equals(InstanceStateName.RUNNING) ||
                        instance.state().name().equals(InstanceStateName.PENDING))
                .map(Instance::instanceId)
                .toList();
    }

    public List<String> terminateAllInstances(String name) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
            .filters(Filter.builder().name("tag:Name").values(name).build())
            .build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        List<String> instanceIds = response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.state().name().equals(InstanceStateName.RUNNING) ||
                        instance.state().name().equals(InstanceStateName.PENDING))
                .map(Instance::instanceId)
                .toList();
        if (instanceIds.isEmpty()) {
            return new ArrayList<>();
        }
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceIds)
                .build();
        return ec2Client.terminateInstances(terminateRequest).terminatingInstances().stream().
                map(InstanceStateChange::instanceId).toList();
    }

    public List<String> terminateInstances(String name, int amount) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(Filter.builder().name("tag:Name").values(name).build())
                .build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        List<String> instanceIds = response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .filter(instance -> instance.state().name().equals(InstanceStateName.RUNNING) ||
                        instance.state().name().equals(InstanceStateName.PENDING))
                .map(Instance::instanceId)
                .limit(amount).toList();
        if (instanceIds.isEmpty()) {
            return null;
        }
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceIds)
                .build();
        return ec2Client.terminateInstances(terminateRequest).terminatingInstances().stream().
                map(InstanceStateChange::instanceId).toList();
    }

    public List<String> terminateInstances(List<String> instancesId) {
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instancesId)
                .build();
        return ec2Client.terminateInstances(terminateRequest).terminatingInstances().stream().
                map(InstanceStateChange::instanceId).toList();
    }

    private static String getEC2userData(List<String> userDataCommands) {
        String userData = String.join("\n", userDataCommands);
        String base64UserData = null;
        try {
            base64UserData = new String( Base64.getEncoder().encode(userData.getBytes("UTF-8")), "UTF-8" );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return base64UserData;
    }

    public String getInstanceId() {
        return EC2MetadataUtils.getInstanceInfo().getInstanceId();
    }
}
