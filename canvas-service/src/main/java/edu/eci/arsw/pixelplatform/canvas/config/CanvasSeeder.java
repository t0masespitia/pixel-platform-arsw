package edu.eci.arsw.pixelplatform.canvas.config;

import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasConstants;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CanvasSeeder implements ApplicationRunner {

    private final CanvasRepository canvasRepository;

    @Value("${canvas.general.width}")
    private int generalWidth;

    @Value("${canvas.general.height}")
    private int generalHeight;

    public CanvasSeeder(CanvasRepository canvasRepository) {
        this.canvasRepository = canvasRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (canvasRepository.findById(CanvasConstants.GENERAL_CANVAS_ID).isEmpty()) {
            Canvas general = new Canvas();
            general.setId(CanvasConstants.GENERAL_CANVAS_ID);
            general.setName("General");
            general.setOwnerId(null);
            general.setWidth(generalWidth);
            general.setHeight(generalHeight);
            general.setPrivate(false);
            general.setCreatedAt(Instant.now());
            canvasRepository.save(general);
        }
    }
}
