package bgu.ds.common;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;

public class S3ObjectOperations {
    private final S3Client s3Client;
    private static final Region region = Region.US_WEST_2;

    private static final S3ObjectOperations instance = new S3ObjectOperations();

    private S3ObjectOperations() {
        s3Client = S3Client.builder().region(region).build();
    }

    public static S3ObjectOperations getInstance() {
        return instance;
    }

    public boolean createBucketIfNotExists(String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.fromValue(region.id()))
                                    .build())
                    .build());
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            return true;
        } catch (BucketAlreadyExistsException e) {
            return false;
        }
    }

    public void putS3Object(String bucketName, String key, String objectPath) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(),
                RequestBody.fromFile(new File(objectPath)));
    }

    public byte[] getS3ObjectBytes(String bucketName, String key, String objectPath) {
        ResponseBytes<GetObjectResponse> objectBytes =
                s3Client.getObjectAsBytes(GetObjectRequest.builder()
                                            .bucket(bucketName)
                                            .key(key)
                                            .range("bytes=0-100")
                                            .build());
        return objectBytes.asByteArray();
    }
}
