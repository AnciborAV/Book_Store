package ru.aav.library;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.io.IOUtils;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Produces a display image only; original PDF bytes are never rewritten. */
@Component
public class LibraryFiles {
    public static final long MAX_PDF = 500L * 1024 * 1024;
    private final Semaphore processing = new Semaphore(1);
    public record Prepared(Path pdf, byte[] cover, int pages, long size) implements AutoCloseable {
        @Override public void close() throws IOException { if (pdf != null) Files.deleteIfExists(pdf); }
    }

    public Prepared prepare(MultipartFile pdf, MultipartFile cover) {
        boolean hasPdf = pdf != null && !pdf.isEmpty();
        boolean hasCover = cover != null && !cover.isEmpty();
        if (!hasPdf && !hasCover) return new Prepared(null, null, 0, 0);
        if (hasPdf && pdf.getSize() > MAX_PDF) throw bad("PDF должен быть не больше 500 МБ");
        if (!processing.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
            "Другая книга ещё обрабатывается. Повторите загрузку немного позже.");
        Path temp = null;
        boolean prepared = false;
        try {
            byte[] image = hasCover ? cover(cover) : null;
            if (!hasPdf) return new Prepared(null, image, 0, 0);
            temp = Files.createTempFile("java-library-", ".pdf");
            pdf.transferTo(temp);
            byte[] signature;
            try (var in = Files.newInputStream(temp)) { signature = in.readNBytes(5); }
            if (!java.util.Arrays.equals(signature, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw bad("Выберите настоящий PDF-файл");
            try (var document = Loader.loadPDF(temp.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
                if (document.isEncrypted()) throw bad("Загрузите PDF без пароля и шифрования");
                int pages = document.getNumberOfPages();
                if (pages < 1) throw bad("В PDF нет страниц");
                if (image == null) {
                    var box = document.getPage(0).getCropBox();
                    float side = Math.max(box.getWidth(), box.getHeight());
                    if (!Float.isFinite(side) || side <= 0 || side > 20000) throw bad("Некорректный размер первой страницы PDF");
                    var renderer = new PDFRenderer(document);
                    renderer.setSubsamplingAllowed(true);
                    image = png(renderer.renderImage(0, Math.min(2f, 1200f / side), ImageType.RGB));
                }
                prepared = true;
                return new Prepared(temp, image, pages, Files.size(temp));
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw bad("Не удалось прочитать файл. Проверьте PDF и обложку; PDF должен быть без пароля.");
        } finally {
            processing.release();
            if (!prepared && temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { temp.toFile().deleteOnExit(); }
            }
        }
    }

    private byte[] cover(MultipartFile file) throws IOException {
        if (file.getSize() > 2L * 1024 * 1024) throw bad("Обложка должна быть не больше 2 МБ");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("Обложка должна быть PNG или JPEG");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw bad("Обложка должна быть PNG или JPEG");
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 4096 || h > 4096) throw bad("Обложка должна быть не больше 4096 × 4096 пикселей");
                var original = reader.read(0);
                double scale = Math.min(1d, 1200d / Math.max(w, h));
                var result = new BufferedImage(Math.max(1,(int)(w*scale)), Math.max(1,(int)(h*scale)), BufferedImage.TYPE_INT_RGB);
                var g = result.createGraphics();
                try {
                    g.setColor(Color.WHITE); g.fillRect(0,0,result.getWidth(),result.getHeight());
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(original,0,0,result.getWidth(),result.getHeight(),null);
                } finally { g.dispose(); }
                return png(result);
            } finally { reader.dispose(); }
        }
    }
    private byte[] png(BufferedImage image) throws IOException {
        var out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
