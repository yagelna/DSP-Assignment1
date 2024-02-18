package bgu.ds.common.sqs.protocol;

import java.util.List;

public class ReviewResult {
    private String link;
    private Integer sentiment;
    private boolean sarcasm;
    private List<String> entities;

    public ReviewResult(String link, Integer sentiment, boolean sarcasm, List<String> entities) {
        this.link = link;
        this.sentiment = sentiment;
        this.sarcasm = sarcasm;
        this.entities = entities;
    }

    public String getLink() {
        return link;
    }

    public Integer getSentiment() {
        return sentiment;
    }

    public boolean getSarcasm() {
        return sarcasm;
    }

    public List<String> getEntities() {
        return entities;
    }
}
