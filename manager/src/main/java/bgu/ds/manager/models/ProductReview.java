package bgu.ds.manager.models;

public class ProductReview {
    private String productName;
    private Review[] reviews;

    public ProductReview(String productName, Review[] reviews) {
        this.productName = productName;
        this.reviews = reviews;
    }

    public String getProductName() {
        return productName;
    }

    public Review[] getReviews() {
        return reviews;
    }
}
