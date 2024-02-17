package bgu.ds.common.sqs.protocol;

public class ReviewCompleteMessage extends SqsMessage {
    private String id;
    private ReviewResult reviewResult;

    public ReviewCompleteMessage(String id, ReviewResult reviewResult) {
        super(SqsMessageType.REVIEW_COMPLETE);
        this.id = id;
        this.reviewResult = reviewResult;
    }

    public String getId() {
        return id;
    }

    public ReviewResult getReviewResult() {
        return reviewResult;
    }
}
