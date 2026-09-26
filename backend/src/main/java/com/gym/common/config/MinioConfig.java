package com.gym.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bucket dùng để lưu ảnh chân dung là private theo mặc định của MinIO — chỉ backend (giữ
 * access key/secret key) đọc/ghi được, client không bao giờ nối thẳng tới MinIO.
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(AppProperties appProperties) throws Exception {
        AppProperties.Storage storage = appProperties.storage();
        MinioClient client = MinioClient.builder()
                .endpoint(storage.endpoint())
                .credentials(storage.accessKey(), storage.secretKey())
                .build();

        if (storage.virtualStyle()) {
            client.enableVirtualStyleEndpoint();
        }

        boolean bucketExists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(storage.bucket())
                .build());
        if (!bucketExists) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(storage.bucket())
                    .build());
            log.info("Đã tạo bucket MinIO mới: {}", storage.bucket());
        }

        return client;
    }
}
