package bgu.ds.manager.models;

import java.util.Date;

public class Review {
    private String id;
    private String link;
    private String title;
    private String text;
    private int rating;
    private String author;
    private Date date;

    public Review(String id, String link, String title, String text, int rating, String author, Date date) {
        this.id = id;
        this.link = link;
        this.title = title;
        this.text = text;
        this.rating = rating;
        this.author = author;
        this.date = date;
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

    public String getAuthor() {
        return author;
    }

    public Date getDate() {
        return date;
    }
}
