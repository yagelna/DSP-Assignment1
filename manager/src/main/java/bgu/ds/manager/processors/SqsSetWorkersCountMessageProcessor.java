package bgu.ds.manager.processors;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.SetWorkersCountMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.manager.Manager;

public class SqsSetWorkersCountMessageProcessor implements SqsMessageProcessor {
    private final static Manager manager = Manager.getInstance();

    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.SET_WORKERS_COUNT) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        manager.setTasksPerWorker(((SetWorkersCountMessage) message).getWorkersCount());
    }
}
