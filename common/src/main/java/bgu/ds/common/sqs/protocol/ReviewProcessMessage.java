package bgu.ds.common.sqs.protocol;

public class ReviewProcessMessage extends SqsMessage{
    private String id;
    private String title;
    private String text;

    public ReviewProcessMessage(String id, String title, String text) {
        super(SqsMessageType.REVIEW_PROCESS);
        this.id = id;
        this.title = title;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }
}
