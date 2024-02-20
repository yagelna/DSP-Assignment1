package bgu.ds.worker.handlers;

import bgu.ds.common.awssdk.Ec2Operations;
import bgu.ds.common.awssdk.SqsOperations;
import bgu.ds.common.sqs.protocol.KeepAliveMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class KeepAliveHandler extends Thread {
    private final static Logger log = LoggerFactory.getLogger(KeepAliveHandler.class);
    private final static Ec2Operations ec2 = Ec2Operations.getInstance();
    private final static SqsOperations sqs = SqsOperations.getInstance();

    private final String keepAliveQueueUrl;
    private final int keepAliveInterval;
    private volatile boolean terminate = false;

    public KeepAliveHandler(String keepAliveQueueUrl, int keepAliveInterval){
        this.keepAliveQueueUrl = keepAliveQueueUrl;
        this.keepAliveInterval = keepAliveInterval;
    }

    @Override
    public void run() {
        String instanceId = ec2.getInstanceId();
        if (instanceId == null) {
            log.warn("Can't get instance id, keep alive handler will not start");
            return;
        }

        while (!terminate) {
            try {
                sqs.sendMessage(keepAliveQueueUrl, new KeepAliveMessage(instanceId, new Date()));
                Thread.sleep(keepAliveInterval * 1000L);
            } catch (InterruptedException e) {
                log.warn("Keep alive handler was interrupted");
                break;
            } catch (Exception e) {
                log.error("Keep alive handler failed to send keep alive message", e);
            }
        }
    }

    public void terminate() {
        terminate = true;
        this.interrupt();
    }
}
