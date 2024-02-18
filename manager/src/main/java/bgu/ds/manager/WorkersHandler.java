package bgu.ds.manager;

import bgu.ds.common.awssdk.Ec2Operations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class WorkersHandler extends Thread {
    private final static Logger logger = LoggerFactory.getLogger(WorkersHandler.class);
    private final static Manager manager = Manager.getInstance();
    private final static ManagerAWSConfig config = AWSConfigProvider.getConfig();
    private final static Ec2Operations ec2 = Ec2Operations.getInstance();

    private volatile boolean terminate = false;
    private final int minWorkers;
    private final int maxWorkers;
    private final int threadSleepTime;
    private AtomicInteger workersCount = new AtomicInteger(0);
    private AtomicInteger tasksPerWorker = new AtomicInteger(0);


    public WorkersHandler(int minWorkers, int maxWorkers, int threadSleepTime) {
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.threadSleepTime = threadSleepTime;
    }

    private void addWorker() {
        int value = workersCount.getAndUpdate(count -> Math.min(count + 1, maxWorkers));
        if (value < maxWorkers) {
            try {
                ec2.createInstance(config.ec2Name(), config.instanceType(), config.ami(), config.instanceProfileName(),
                        config.securityGroupId(), config.userDataCommands());
            } catch (Exception e) {
                logger.warn("Failed to create worker instance {}", e.getMessage());
                workersCount.getAndDecrement();
            }
        }
    }

    private void removeWorker() {
        int value = workersCount.getAndUpdate(count -> Math.max(count - 1, minWorkers));
        if (value > minWorkers) {
            try {
                ec2.terminateInstance(config.ec2Name());
            } catch (Exception e) {
                logger.warn("Failed to terminate worker instance {}", e.getMessage());
                workersCount.getAndIncrement();
            }
        }
    }

    private void stopAllWorkers() {
        if (workersCount.get() > 0) {
            ec2.terminateAllInstances(config.ec2Name());
        }
    }

    public void setTasksPerWorker(int tasksPerWorker) {
        this.tasksPerWorker.set(tasksPerWorker);
    }

    @Override
    public void run() {
        for (int i = 0; i < minWorkers; i++) {
            addWorker();
        }
        while (!terminate) {
            try {
                int pendingReviews = manager.getPendingReviewsCount();
                int workersNeeded = (int) Math.ceil((double) pendingReviews / tasksPerWorker.get());
                int workersDiff = workersNeeded - workersCount.get();
                if (workersDiff > 0) {
                    for (int i = 0; i < workersDiff; i++) {
                        addWorker();
                    }
                } else if (workersDiff < 0) {
                    // We only stop one worker per iteration
                    removeWorker();
                }
                Thread.sleep(threadSleepTime);
            } catch (InterruptedException e) {
                logger.info("WorkersHandler thread is interrupted");
            }
        }
        stopAllWorkers();
    }

    public void shutdown() {
        terminate = true;
        this.interrupt();
    }
}
