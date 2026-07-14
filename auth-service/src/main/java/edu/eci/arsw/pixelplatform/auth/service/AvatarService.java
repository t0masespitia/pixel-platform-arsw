package edu.eci.arsw.pixelplatform.auth.service;

import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class AvatarService {

    // Extensiones que pudieron haber quedado de antes de este cambio (para
    // poder borrarlas al reemplazar el avatar). El formato de salida ahora
    // SIEMPRE es PNG, sin importar el formato de entrada.
    private static final Set<String> LEGACY_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp");
    private static final long MAX_SIZE_BYTES = 15L * 1024 * 1024;

    // Tamano al que se reduce la imagen antes de reescalarla: cuanto mas chico,
    // mas se notan los "bloques" del efecto pixel art.
    private static final int PIXELATE_SIZE = 32;
    // Tamano final del avatar guardado (cuadrado).
    private static final int OUTPUT_SIZE = 256;

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

        BufferedImage source = ImageIO.read(file.getInputStream());
        if (source == null) {
            throw new IllegalArgumentException("No se pudo leer la imagen. Formatos soportados: PNG, JPG, GIF, BMP");
        }

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        BufferedImage pixelated = pixelate(source);

        deleteExistingAvatarFiles(userId);

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String filename = userId + ".png";
        Path target = dir.resolve(filename);
        ImageIO.write(pixelated, "png", target.toFile());

        String avatarUrl = "/api/auth/avatars/" + filename;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return avatarUrl;
    }

    /**
     * Recorta al cuadrado central de la imagen, la reduce con suavizado (para
     * promediar colores en vez de solo tomar un pixel de muestra) y despues la
     * vuelve a agrandar sin suavizado (vecino mas cercano), lo que produce el
     * efecto clasico de "bloques" de pixel art.
     */
    private BufferedImage pixelate(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - side) / 2;
        int cropY = (source.getHeight() - side) / 2;
        BufferedImage cropped = source.getSubimage(cropX, cropY, side, side);

        BufferedImage small = new BufferedImage(PIXELATE_SIZE, PIXELATE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gSmall = small.createGraphics();
        gSmall.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gSmall.drawImage(cropped, 0, 0, PIXELATE_SIZE, PIXELATE_SIZE, null);
        gSmall.dispose();

        BufferedImage output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gOut = output.createGraphics();
        gOut.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        gOut.drawImage(small, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
        gOut.dispose();

        return output;
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
        for (String ext : LEGACY_EXTENSIONS) {
            Files.deleteIfExists(dir.resolve(userId + "." + ext));
        }
    }
}
