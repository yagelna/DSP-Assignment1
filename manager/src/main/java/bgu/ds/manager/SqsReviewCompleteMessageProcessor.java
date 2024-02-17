package bgu.ds.manager;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.ReviewCompleteMessage;
import bgu.ds.common.sqs.protocol.SqsMessage;
import bgu.ds.common.sqs.protocol.SqsMessageType;

public class SqsReviewCompleteMessageProcessor implements SqsMessageProcessor {
    private final static Manager manager = Manager.getInstance();

    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.REVIEW_COMPLETE) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        ReviewCompleteMessage reviewCompleteMessage = (ReviewCompleteMessage) message;
        manager.completeReview(reviewCompleteMessage.getId(), reviewCompleteMessage.getReviewResult());
    }
}
