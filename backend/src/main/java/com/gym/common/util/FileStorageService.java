package com.gym.common.util;

import com.gym.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Set;

/**
 * Quản lý lưu trữ và phục vụ file cục bộ (ảnh chân dung hội viên, v.v.).
 */
@Slf4j
@Service
public class FileStorageService {

    private static final String UPLOAD_DIR = "uploads/photos";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Path rootLocation;

    public FileStorageService() {
        this.rootLocation = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            log.error("Không thể tạo thư mục lưu trữ file: {}", this.rootLocation, e);
            throw new RuntimeException("Không thể khởi tạo thư mục lưu trữ ảnh", e);
        }
    }

    /**
     * Lưu ảnh chân dung của Person và trả về photoKey.
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

        String filename = String.format("person_%d_%d.%s", personId, System.currentTimeMillis(), ext);
        Path destination = this.rootLocation.resolve(filename).normalize();

        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Lưu thành công ảnh chân dung: {}", filename);
            return filename;
        } catch (IOException e) {
            log.error("Lỗi khi lưu file ảnh: {}", destination, e);
            throw ApiException.badRequest("STORAGE_ERROR", "Không thể lưu file ảnh, vui lòng thử lại");
        }
    }

    /**
     * Xóa file ảnh cũ khỏi đĩa. Không ném lỗi nếu file không còn tồn tại —
     * mục tiêu là dọn rác, không phải điều kiện bắt buộc phải thành công.
     */
    public void deletePhoto(String photoKey) {
        if (photoKey == null || photoKey.isBlank()) return;
        try {
            Path filePath = this.rootLocation.resolve(photoKey).normalize();
            if (!filePath.startsWith(this.rootLocation)) {
                log.warn("Bỏ qua xóa file có đường dẫn không hợp lệ: {}", photoKey);
                return;
            }
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Không thể xóa file ảnh cũ {}: {}", photoKey, e.getMessage());
        }
    }

    /**
     * Đọc file ảnh dưới dạng Resource để stream về trình duyệt.
     */
    public Resource loadAsResource(String photoKey) {
        try {
            // Ngăn chặn Path Traversal
            Path filePath = this.rootLocation.resolve(photoKey).normalize();
            if (!filePath.startsWith(this.rootLocation)) {
                throw ApiException.notFound("Đường dẫn file không hợp lệ");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw ApiException.notFound("Không tìm thấy ảnh trên hệ thống");
            }
        } catch (MalformedURLException e) {
            throw ApiException.notFound("Đường dẫn file không hợp lệ");
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
