package org.ticketsouq.eventservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PosterStorageService {

    private final Path uploadDir;

    public PosterStorageService(@Value("${app.poster.upload-dir:uploads/posters}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new ConflictException("Could not create upload directory: " + e.getMessage());
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String filename = UUID.randomUUID() + extension;

        try {
            Path target = uploadDir.resolve(filename);
            file.transferTo(target.toFile());
            return "/uploads/posters/" + filename;
        } catch (IOException e) {
            throw new ConflictException("Could not save poster: " + e.getMessage());
        }
    }
}
