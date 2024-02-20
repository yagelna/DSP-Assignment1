package bgu.ds.worker.processors;

import bgu.ds.common.sqs.SqsMessageProcessor;
import bgu.ds.common.sqs.protocol.*;
import bgu.ds.worker.Worker;

public class SqsReviewProcessMessageProcessor implements SqsMessageProcessor {
    private final Worker worker;

    public SqsReviewProcessMessageProcessor(Worker worker) {
        this.worker = worker;
    }

    @Override
    public void process(SqsMessage message) {
        if (message.getMessageType() != SqsMessageType.REVIEW_PROCESS) {
            throw new IllegalArgumentException("Invalid message type: " + message.getMessageType());
        }
        ReviewProcessMessage reviewProcessMessage = (ReviewProcessMessage) message;
        worker.processReview(reviewProcessMessage.getId(), reviewProcessMessage.getTitle(),
                reviewProcessMessage.getText(), reviewProcessMessage.getLink(), reviewProcessMessage.getRating());
    }
}
