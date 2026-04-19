package com.netmaster.nmx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CompanyLogoStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp",
            "image/svg+xml"
    );

    private final Path storageRoot;

    public CompanyLogoStorageService(@Value("${nmx.company.logo-dir:uploads/company-logos}") String storageDir) {
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal menyiapkan folder upload logo company", ex);
        }
    }

    public String storeLogo(MultipartFile file, String previousLogo) {
        validateFile(file);

        String extension = extractExtension(file.getOriginalFilename(), file.getContentType());
        String storedFilename = "company-logo-" + UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storedFilename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            deleteIfExists(previousLogo);
            return storedFilename;
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal menyimpan logo company", ex);
        }
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = resolveSafePath(filename);
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("Logo company tidak ditemukan");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Logo company tidak dapat dibaca");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Gagal membaca logo company", ex);
        }
    }

    public MediaType detectMediaType(String filename) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lowerName.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public void deleteIfExists(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(resolveSafePath(filename));
        } catch (Exception ex) {
            log.warn("Gagal menghapus file logo company {}: {}", filename, ex.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File logo wajib dipilih");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Ukuran file logo maksimal 2 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Format logo harus PNG, JPG, WEBP, atau SVG");
        }
    }

    private String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }

        if (MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
            return ".png";
        }
        if (MediaType.IMAGE_JPEG_VALUE.equals(contentType)) {
            return ".jpg";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        if ("image/svg+xml".equals(contentType)) {
            return ".svg";
        }
        return ".img";
    }

    private Path resolveSafePath(String filename) {
        Path file = storageRoot.resolve(filename).normalize();
        if (!file.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Path file logo tidak valid");
        }
        return file;
    }
}
