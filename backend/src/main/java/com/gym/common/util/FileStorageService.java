package com.gym.common.util;

import com.gym.common.config.AppProperties;
import com.gym.common.exception.ApiException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;

/**
 * Quản lý lưu trữ và phục vụ ảnh chân dung hội viên qua MinIO (S3-compatible).
 * Bucket là private — backend luôn đọc/ghi bằng access key riêng, không có URL công khai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final MinioClient minioClient;
    private final AppProperties appProperties;

    /**
     * Lưu ảnh chân dung của Person và trả về photoKey (= tên object trong bucket).
     */
    public String storePhoto(Long personId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_EMPTY", "Vui lòng chọn file ảnh để tải lên");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Dung lượng ảnh không được vượt quá 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "Chỉ chấp nhận file ảnh định dạng JPG, PNG hoặc WEBP");
        }

        String ext = "jpg";
        if (contentType.equalsIgnoreCase("image/png")) {
            ext = "png";
        } else if (contentType.equalsIgnoreCase("image/webp")) {
            ext = "webp";
        }

        String objectKey = String.format("person_%d_%d.%s", personId, System.currentTimeMillis(), ext);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(appProperties.storage().bucket())
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1L)
                    .contentType(contentType)
                    .build());
            log.info("Lưu thành công ảnh chân dung lên MinIO: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("Lỗi khi tải ảnh lên MinIO: {}", objectKey, e);
            throw ApiException.badRequest("STORAGE_ERROR", "Không thể lưu file ảnh, vui lòng thử lại");
        }
    }

    /**
     * Xóa object ảnh cũ khỏi bucket. Không ném lỗi nếu không xóa được —
     * mục tiêu là dọn rác, không phải điều kiện bắt buộc phải thành công.
     */
    public void deletePhoto(String photoKey) {
        if (photoKey == null || photoKey.isBlank()) return;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(appProperties.storage().bucket())
                    .object(photoKey)
                    .build());
        } catch (Exception e) {
            log.warn("Không thể xóa ảnh cũ {} trên MinIO: {}", photoKey, e.getMessage());
        }
    }

    /**
     * Đọc ảnh từ MinIO dưới dạng Resource để stream về trình duyệt qua FileController.
     */
    public Resource loadAsResource(String photoKey) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(appProperties.storage().bucket())
                    .object(photoKey)
                    .build());
            return new InputStreamResource(stream);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw ApiException.notFound("Không tìm thấy ảnh trên hệ thống");
            }
            log.error("Lỗi MinIO khi đọc ảnh {}: {}", photoKey, e.getMessage());
            throw ApiException.notFound("Không tìm thấy ảnh trên hệ thống");
        } catch (Exception e) {
            log.error("Lỗi khi đọc ảnh {} từ MinIO: {}", photoKey, e.getMessage());
            throw ApiException.notFound("Không tìm thấy ảnh trên hệ thống");
        }
    }

    /**
     * Xác định MediaType dựa vào đuôi file.
     */
    public MediaType getMediaType(String photoKey) {
        if (photoKey == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = photoKey.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
