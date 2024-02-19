package bgu.ds.local.processors;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.SendOutputMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.local.LocalApp;

public class SqsOutputMessageProcessor implements SqsMessageProcessor {
    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.SEND_OUTPUT) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        SendOutputMessage outputMessage = (SendOutputMessage) message;
        LocalApp.getInstance().addOutputFile(outputMessage.getInputId(), outputMessage.getBucketName(),
                outputMessage.getObjectKey());
    }
}
