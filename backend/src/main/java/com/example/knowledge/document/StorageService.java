package com.example.knowledge.document;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {
    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("pdf", "docx", "pptx", "txt", "md");
    private final Path root;

    public StorageService(@Value("${knowledge.upload-dir:./uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public static String validate(String fileName, long fileSize) {
        if (fileSize > MAX_SIZE) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        String extension = extension(fileName);
        if (!ALLOWED.contains(extension)) throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        return extension;
    }

    public StoredFile save(Long knowledgeBaseId, MultipartFile file) throws IOException {
        String extension = validate(file.getOriginalFilename(), file.getSize());
        Path directory = root.resolve(String.valueOf(knowledgeBaseId)).normalize();
        if (!directory.startsWith(root)) throw new BusinessException(ErrorCode.BAD_REQUEST);
        Files.createDirectories(directory);
        Path target = directory.resolve(UUID.randomUUID() + "." + extension).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(target.toString(), extension, file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename());
    }

    public void delete(String path) throws IOException {
        Path target = Path.of(path).toAbsolutePath().normalize();
        if (target.startsWith(root)) Files.deleteIfExists(target);
    }

    public static String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String path, String extension, String originalName) {}
}

