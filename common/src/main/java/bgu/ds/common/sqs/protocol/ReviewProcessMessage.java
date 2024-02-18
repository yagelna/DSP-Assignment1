package bgu.ds.common.sqs.protocol;

public class ReviewProcessMessage extends SqsMessage{
    private String id;
    private String link;
    private String title;
    private String text;
    private int rating;

    public ReviewProcessMessage(String id, String link, String title, String text, int rating) {
        super(SqsMessageType.REVIEW_PROCESS);
        this.id = id;
        this.link = link;
        this.title = title;
        this.text = text;
        this.rating = rating;
    }

    public String getId() {
        return id;
    }

    public String getLink() {
        return link;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public int getRating() {
        return rating;
    }
}
