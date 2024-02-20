package bgu.ds.manager.processors;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.ReviewCompleteMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;
import bgu.ds.manager.Manager;

public class SqsReviewCompleteMessageProcessor implements SqsMessageProcessor {
    private final Manager manager;

    public SqsReviewCompleteMessageProcessor(Manager manager) {
        this.manager = manager;
    }

    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.REVIEW_COMPLETE) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        ReviewCompleteMessage reviewCompleteMessage = (ReviewCompleteMessage) message;
        manager.completeReview(reviewCompleteMessage.getId(), reviewCompleteMessage.getReviewResult());
    }
}
