package bgu.ds.manager.handlers;

import bgu.ds.common.awssdk.Ec2Operations;
import bgu.ds.common.sqs.SqsMessageConsumer;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.manager.Manager;
import bgu.ds.manager.config.AWSConfigProvider;
import bgu.ds.manager.config.ManagerAWSConfig;
import bgu.ds.manager.processors.KeepAliveMessageProcessor;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkersHandler extends Thread {
    private final static Logger logger = LoggerFactory.getLogger(WorkersHandler.class);
    private final static ManagerAWSConfig config = AWSConfigProvider.getConfig();
    private final static Ec2Operations ec2 = Ec2Operations.getInstance();

    private final Manager manager;
    private volatile boolean terminate = false;
    private final int minWorkers;
    private final int maxWorkers;
    private final String keepAliveQueueUrl;
    private final int keepAliveTimeout;
    private final int bootGracefulTime;
    private final int reduceThreshold;
    private final int threadSleepTime;
    private final AtomicInteger tasksPerWorker = new AtomicInteger(1);
    private final Map<String, Date> workers = new ConcurrentHashMap<>();


    public WorkersHandler(Manager manager, int minWorkers, int maxWorkers, String keepAliveQueueUrl, int keepAliveTimeout,
                          int bootGracefulTime, int reduceThreshold, int threadSleepTime) {
        this.manager = manager;
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.keepAliveQueueUrl = keepAliveQueueUrl;
        this.keepAliveTimeout = keepAliveTimeout;
        this.bootGracefulTime = bootGracefulTime;
        this.reduceThreshold = reduceThreshold;
        this.threadSleepTime = threadSleepTime;
    }

    private void addWorkers(int count) {
        // There is only one thread that adds workers, so we don't need to synchronize the workers map
        if (workers.size() >= maxWorkers) {
            logger.info("Can't add {} workers, max workers count is {}", count, maxWorkers);
            return;
        }
        if (workers.size() + count > maxWorkers) {
            logger.info("Can't add {} workers, adding {} workers instead", count, maxWorkers - workers.size());
            count = maxWorkers - workers.size();
        }
        List<String> instancesId = ec2.createInstance(config.ec2Name(), config.instanceType(), config.ami(),
                1, count, config.instanceProfileName(), config.securityGroupName(), config.userDataCommands());
        instancesId.forEach(instanceId -> workers.put(instanceId, DateUtils.addSeconds(new Date(), bootGracefulTime)));
        logger.info("Added {} workers with Ids {}", instancesId.size(), String.join(", ", instancesId));
    }

    private void removeWorkers(int count) {
        // There is only one thread that removes workers, so we don't need to synchronize the workers map
        if (workers.size() <= minWorkers) {
            logger.info("Can't remove {} workers, min workers count is {}", count, minWorkers);
            return;
        }
        if (workers.size() - count < minWorkers) {
            logger.info("Can't remove {} workers, removing {} workers instead", count, workers.size() - minWorkers);
            count = workers.size() - minWorkers;
        }
        List<String> instancesId = ec2.terminateInstances(config.ec2Name(), count);
        instancesId.forEach(workers::remove);
        logger.info("Removed {} workers with Ids {}", instancesId.size(), String.join(", ", instancesId));
    }

    private void stopAllWorkers() {
        if (workers.size() > 0) {
            ec2.terminateAllInstances(config.ec2Name());
            workers.clear();
        }
    }

    public void setTasksPerWorker(int tasksPerWorker) {
        this.tasksPerWorker.set(tasksPerWorker);
    }

    public void updateWorkerKeepAlive(String instanceId, Date date) {
        if (workers.computeIfPresent(instanceId, (k, v) -> date) != null) {
            logger.info("Updated worker {} keep alive time to {}", instanceId, date);
        } else {
            logger.warn("Received keep alive for an unmanaged worker {} with time {}", instanceId, date);
        }
    }

    private void terminateNoneResponsiveWorkers() {
        Date now = new Date();
        List<String> toTerminate = new ArrayList<>();
        workers.forEach((instanceId, date) -> {
            if (now.getTime() - date.getTime() > keepAliveTimeout * 1000L) {
                logger.warn("Worker {} is not responsive, terminating it", instanceId);
                toTerminate.add(instanceId);
            }
        });

        if (!toTerminate.isEmpty()) {
            List<String> instancesId = ec2.terminateInstances(toTerminate);
            instancesId.forEach(workers::remove);
            logger.info("Terminated {} workers with Ids {} due to keep alive timeout",
                    instancesId.size(), String.join(", ", instancesId));
        }
    }

    @Override
    public void run() {
        // Check the number of running workers and if necessary add the minimum required workers
        ec2.getRunningInstancesIds(config.ec2Name()).forEach(instanceId -> workers.put(instanceId, new Date()));
        if (workers.size() < minWorkers) {
            addWorkers(minWorkers - workers.size());
        }

        SqsMessageConsumer keepAliveConsumer = new SqsMessageConsumer(keepAliveQueueUrl, config.consumerThreads(),
                config.consumerVisibilityTimeout(), config.consumerMaxVisibilityExtensionTime(),
                config.consumerVisibilityThreadSleepTime());
        keepAliveConsumer.registerProcessor(SqsMessageType.KEEP_ALIVE, new KeepAliveMessageProcessor(this));
        keepAliveConsumer.start();

        while (!terminate) {
            try {
                logger.info("Checking workers status");
                // Check if there are workers that are not responsive and terminate them
                terminateNoneResponsiveWorkers();
                // Check the number of running workers and if necessary add to reach min workers
                if (workers.size() < minWorkers) {
                    addWorkers(minWorkers - workers.size());
                }

                // Check the number of pending reviews and if necessary add or remove workers
                int pendingReviews = manager.getPendingReviewsCount();
                int workersNeeded = (int) Math.ceil((double) pendingReviews / tasksPerWorker.get());
                int workersDiff = workersNeeded - workers.size();
                if (workersDiff > 0) {
                    addWorkers(workersDiff);
                } else if (workersDiff < 0) {
                    removeWorkers(Math.min(Math.abs(workersDiff), reduceThreshold));
                }
                Thread.sleep(threadSleepTime * 1000L);
            } catch (InterruptedException e) {
                logger.info("WorkersHandler thread was interrupted");
            } catch (Exception e) {
                logger.error("An error occurred in WorkersHandler thread", e);
            }
        }
        keepAliveConsumer.shutdown();
        stopAllWorkers();
    }

    public void shutdown() {
        terminate = true;
        this.interrupt();
    }
}
