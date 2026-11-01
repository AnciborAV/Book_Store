package ru.aav.library;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpRange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** HTTP delivery runs inside the repeatable-read transaction of JavaLibraryService. */
@Service
@RequiredArgsConstructor
public class PdfDeliveryService {
    private static final int CHUNK_SIZE = 1024 * 1024;

    private final JdbcTemplate jdbc;

    void download(JavaLibraryService.Book book, HttpServletResponse response) throws IOException {
        String name = book.title().replaceAll("[\\p{Cntrl}/\\\\]", "_") + ".pdf";
        response.setContentType("application/pdf");
        response.setContentLengthLong(book.fileSize());
        response.setHeader(
            "Content-Disposition",
            ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString()
        );
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        copyCompleteFile(book.id(), book.fileSize(), response.getOutputStream());
    }

    void read(JavaLibraryService.Book book, String range, HttpServletResponse response) throws IOException {
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "no-store");
        ByteRange requested;
        try {
            requested = resolveRange(range, book.fileSize());
        } catch (IllegalArgumentException ex) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + book.fileSize());
            return;
        }
        if (range != null) {
            response.setStatus(206);
            response.setHeader(
                "Content-Range",
                "bytes " + requested.start() + "-" + requested.end() + "/" + book.fileSize()
            );
        }
        response.setContentType("application/pdf");
        response.setContentLengthLong(requested.length());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(
            "Content-Disposition",
            ContentDisposition.inline().filename("book.pdf").build().toString()
        );
        copyRange(book.id(), requested, response.getOutputStream());
    }

    private record ByteRange(long start, long end) {
        long length() {
            return end - start + 1;
        }
    }

    private ByteRange resolveRange(String header, long size) {
        if (header == null) {
            return new ByteRange(0, size - 1);
        }
        var ranges = HttpRange.parseRanges(header);
        if (ranges.size() != 1) {
            throw new IllegalArgumentException();
        }
        long start = ranges.getFirst().getRangeStart(size);
        long end = ranges.getFirst().getRangeEnd(size);
        if (start < 0 || start >= size || end < start) {
            throw new IllegalArgumentException();
        }
        return new ByteRange(start, end);
    }

    private void copyCompleteFile(String id, long size, OutputStream out) throws IOException {
        long remaining = size;
        for (int part = 0; remaining > 0; part++) {
            byte[] block = loadChunk(id, part);
            if (block == null || block.length == 0 || block.length > remaining) {
                throw new IOException("Incomplete PDF storage");
            }
            out.write(block);
            remaining -= block.length;
        }
        out.flush();
    }

    private void copyRange(String id, ByteRange range, OutputStream out) throws IOException {
        for (long offset = range.start(); offset <= range.end();) {
            int part = (int) (offset / CHUNK_SIZE);
            int local = (int) (offset % CHUNK_SIZE);
            byte[] block = loadChunk(id, part);
            if (block == null || local >= block.length) {
                throw new IOException("Incomplete PDF storage");
            }
            int count = (int) Math.min(block.length - local, range.end() - offset + 1);
            out.write(block, local, count);
            offset += count;
        }
        out.flush();
    }

    // One chunk per SELECT; never collect the full PDF into a byte array.
    private byte[] loadChunk(String id, int part) {
        return jdbc.queryForObject(
            "SELECT data FROM java_library_pdf_chunks WHERE book_id=? AND part=?",
            byte[].class, id, part
        );
    }
}
