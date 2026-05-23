package com.hp.mcpserver.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.awt.print.Book;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class ContentExtractor {

    /** Maximum PDF pages parsed per file (avoid huge books freezing the server). */
    private static final int PDF_MAX_PAGES = 5;

    /** Maximum characters read from plain-text files. */
    private static final int TEXT_MAX_CHARS = 200_000;

    /**
     * Attempts to extract text from {@code file}.
     *
     * @return an {@link Optional} containing the extracted text, or empty if the
     *         format is not supported or extraction fails.
     */
    public Optional<String> extract(Path file) {
        String ext = extension(file);
        return switch (ext) {
            case "pdf"  -> extractPdf(file);

            case "txt", "md" -> extractText(file);
            default     -> Optional.empty();
        };
    }


    private Optional<String> extractPdf(Path file) {
    try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (doc.isEncrypted()) {
                // Try with empty password (many "encrypted" PDFs allow blank password)
                doc.setAllSecurityToBeRemoved(true);
            }
            int pages = Math.min(doc.getNumberOfPages(), PDF_MAX_PAGES);
            var stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(pages);
            return Optional.of(stripper.getText(doc));
        } catch (Exception e) {
            return Optional.empty();
        }
    }



    private Optional<String> extractText(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > TEXT_MAX_CHARS) {
                text = text.substring(0, TEXT_MAX_CHARS);
            }
            return Optional.of(text);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Returns the file extension in lower-case, or "" if there is none. */
    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
