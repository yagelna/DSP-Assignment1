package bgu.ds.common.sqs;

import bgu.ds.common.sqs.protocol.SqsMessage;

public interface SqsMessageProcessor {
    void process(SqsMessage message);
}
