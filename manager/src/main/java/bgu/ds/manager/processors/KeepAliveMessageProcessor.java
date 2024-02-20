package bgu.ds.manager.processors;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.KeepAliveMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.manager.handlers.WorkersHandler;

public class KeepAliveMessageProcessor implements SqsMessageProcessor {
    private final WorkersHandler workersHandler;

    public KeepAliveMessageProcessor(WorkersHandler workersHandler){
        this.workersHandler = workersHandler;
    }

    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.KEEP_ALIVE) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        KeepAliveMessage keepAliveMessage = (KeepAliveMessage) message;
        workersHandler.updateWorkerKeepAlive(keepAliveMessage.getInstanceId(), keepAliveMessage.getTimestamp());
    }
}
