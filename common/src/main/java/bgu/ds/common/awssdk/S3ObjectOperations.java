package bgu.ds.common.awssdk;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            return false;
        }
    }

    public String putObject(String filePath, String bucketName) {
        String fileSuffix = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String objectKey = String.format("%s-%s", new File(filePath).getName(), fileSuffix);
        putObject(filePath, bucketName, objectKey);
        return objectKey;
    }

    public void putObject(String filePath, String bucketName, String key) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(),
                RequestBody.fromFile(new File(filePath)));
        s3Client.waiter().waitUntilObjectExists(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    public File getObject(String bucketName, String key) throws IOException {
        File tempFile = File.createTempFile("input-", ".tmp");
        getObject(key, bucketName, tempFile);
        tempFile.deleteOnExit();
        return tempFile;
    }

    public void getObject(String key, String bucketName, File outputFile) throws IOException {
        s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(),
                ResponseTransformer.toOutputStream(Files.newOutputStream(outputFile.toPath())));
        // This is a stupid hack because of aws sdk - you cant pass an existent path to ResponseTransformer.toFile
    }

}
