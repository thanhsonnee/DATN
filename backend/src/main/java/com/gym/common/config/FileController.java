package com.gym.common.config;

import com.gym.common.util.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Tag(name = "Tệp tin", description = "Xem và tải tệp tin hệ thống")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "Xem ảnh chân dung hội viên", description = "Stream ảnh trực tiếp cho thẻ img trên trình duyệt")
    @GetMapping("/photos/{photoKey}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String photoKey) {
        Resource resource = fileStorageService.loadAsResource(photoKey);
        MediaType mediaType = fileStorageService.getMediaType(photoKey);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + TimeUnit.HOURS.toSeconds(24))
                .body(resource);
    }
}
