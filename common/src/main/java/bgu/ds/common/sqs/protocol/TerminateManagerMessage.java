package bgu.ds.common.sqs.protocol;

public class TerminateManagerMessage extends SqsMessage{
    public TerminateManagerMessage() {
        super(SqsMessageType.TERMINATE_MANAGER);
    }
}
