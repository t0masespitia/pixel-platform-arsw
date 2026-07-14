package edu.eci.arsw.pixelplatform.auth.service;

import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class AvatarService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_SIZE_BYTES = 15L * 1024 * 1024;

    private final UserRepository userRepository;
    private final String uploadDir;

    public AvatarService(UserRepository userRepository,
                         @Value("${app.upload-dir}") String uploadDir) {
        this.userRepository = userRepository;
        this.uploadDir = uploadDir;
    }

    public String uploadAvatar(String userId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("La imagen no puede pesar mas de 15MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Formato no soportado. Usa JPG, PNG o WEBP");
        }

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        deleteExistingAvatarFiles(userId);

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String filename = userId + "." + extension;
        Path target = dir.resolve(filename);
        file.transferTo(target);

        String avatarUrl = "/api/auth/avatars/" + filename;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return avatarUrl;
    }

    public void deleteAvatar(String userId) throws IOException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        deleteExistingAvatarFiles(userId);
        user.setAvatarUrl(null);
        userRepository.save(user);
    }

    private void deleteExistingAvatarFiles(String userId) throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) return;
        for (String ext : ALLOWED_EXTENSIONS) {
            Files.deleteIfExists(dir.resolve(userId + "." + ext));
        }
    }
}
