package ru.aav.library;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class LibraryCoverCodecTest {
    @Test void jpeg2000PictureSurvivesPdfCoverRendering() throws Exception {
        // Lossless RGB JPEG 2000 fixture produced with OpenJPEG (100 x 150).
        byte[] encoded = java.util.Base64.getDecoder().decode("AAAADGpQICANCocKAAAAFGZ0eXBqcDIgAAAAAGpwMiAAAAAtanAyaAAAABZpaGRyAAAAlgAAAGQAAwcHAAAAAAAPY29scgEAAAAAABAAAADHanAyY/9P/1EALwAAAAAAZAAAAJYAAAAAAAAAAAAAAGQAAACWAAAAAAAAAAAAAwcBAQcBAQcBAf9SAAwAAAABAAUEBAAB/1wAE0BASEhQSEhQSEhQSEhQSEhQ/2QAJQABQ3JlYXRlZCBieSBPcGVuSlBFRyB2ZXJzaW9uIDIuNS40/5AACgAAAAAAQAAB/5PPtDQRUE7k8jAMJAkS+Eg/wfIEEVBUX8fUEhQAVpdCKAABg4CAgICAgICAgICAgICAgP/Z");
        byte[] pdf;
        try (var doc = new PDDocument()) {
            var page = new PDPage(new PDRectangle(100, 150));
            doc.addPage(page);
            var picture = new PDImageXObject(doc, new ByteArrayInputStream(encoded),
                org.apache.pdfbox.cos.COSName.JPX_DECODE, 100, 150, 8,
                null);
            try (var content = new PDPageContentStream(doc, page)) {
                content.drawImage(picture, 0, 0, 100, 150);
            }
            var out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }
        try (var prepared = new LibraryFiles().prepare(
                new MockMultipartFile("pdf", "book.pdf", "application/pdf", pdf), null)) {
            var cover = ImageIO.read(new ByteArrayInputStream(prepared.cover()));
            var color = new Color(cover.getRGB(cover.getWidth()/2, cover.getHeight()/2));
            assertTrue(color.getRed() < 60 && color.getGreen() > 80 && color.getBlue() > 140,
                "Embedded color image must not disappear into a white cover: " + color);
            assertArrayEquals(pdf, java.nio.file.Files.readAllBytes(prepared.pdf()));
        }
    }
    @Test void jbig2ReaderIsAvailable() {
        assertTrue(ImageIO.getImageReadersByFormatName("JBIG2").hasNext());
    }
}
