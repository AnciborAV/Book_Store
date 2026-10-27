package ru.aav.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;

/** Проверяет формат и размеры до декодирования, сохраняет безопасный PNG 256×256. */
@Component
public class AvatarProcessor {
    public byte[] process(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) throw invalid();
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw invalid();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096) throw invalid();
                var source = reader.read(0);
                int side = Math.min(width, height), x = (width-side)/2, y = (height-side)/2;
                var result = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                var graphics = result.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, 256, 256, x, y, x+side, y+side, null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                ImageIO.write(result, "png", output);
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { throw invalid(); }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужен PNG/JPEG до 2 МБ и 4096×4096 пикселей");
    }
}
