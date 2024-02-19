package bgu.ds.manager.processors;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.AddInputMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.manager.Manager;

import java.io.IOException;

public class SqsInputMessageProcessor implements SqsMessageProcessor {
    private final static Manager manager = Manager.getInstance();

    @Override
    public void process(SqsMessage message) {
        try {
            if (message.getMessageType() != SqsMessageType.ADD_INPUT) {
                throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
            }

            AddInputMessage addInputMessage = (AddInputMessage) message;
            manager.addInputFile(addInputMessage.getInputId(), addInputMessage.getInputBucketName(),
                    addInputMessage.getObjectKey(), addInputMessage.getOutputQueueName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
