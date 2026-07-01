package com.hp.mcpserver.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

@Service
public class ContentExtractor {

    /** Maximum PDF pages parsed per file (avoid huge books freezing the server). */
    private static final int PDF_MAX_PAGES = 5;

    /**
     * Attempts to extract text from a PDF file.
     *
     * @return an {@link Optional} containing the extracted text, or empty if the
     *         file is not a PDF or extraction fails.
     */
    public Optional<String> extract(Path file) {
        return "pdf".equals(extension(file)) ? extractPdf(file) : Optional.empty();
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

    /** Returns the file extension in lower-case, or "" if there is none. */
    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
