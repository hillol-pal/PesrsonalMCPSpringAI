package com.hp.mcpserver.service;

import com.hp.mcpserver.models.BookResult;
import com.hp.mcpserver.models.SearchOptions;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Service
public class BookSearchService {

    /** Extensions treated as "book" files. */
    public static final Set<String> BOOK_EXTENSIONS = Set.of("pdf");

    private final ContentExtractor extractor;

    public BookSearchService(ContentExtractor extractor) {
        this.extractor = extractor;
    }

    /**
     * Finds all book files in {@code rootDir} whose name or content matches
     * one or more of the {@code topicKeywords}.
     *
     * @param rootDir        absolute path to the directory to scan.
     * @param topicKeywords  one or more keywords to search for (split on whitespace).
     * @param options        search configuration.
     * @return list of matching {@link BookResult}s, sorted by relevance then name.
     */
    public List<BookResult> findBooks(Path rootDir,
                                      List<String> topicKeywords,
                                      SearchOptions options) throws IOException {

        validateDirectory(rootDir);

        List<String> keywords = topicKeywords.stream()
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .toList();

        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("At least one keyword is required.");
        }

        var collector = new ArrayList<BookResult>();
        int maxDepth = options.recursive() ? options.maxDepth() : 1;

        Files.walkFileTree(rootDir, Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    processFile(file, attrs.size(), keywords, options)
                            .ifPresent(collector::add);
                }
                if (options.maxResults() > 0 && collector.size() >= options.maxResults()) {
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Skip unreadable files silently
                return FileVisitResult.CONTINUE;
            }
        });

        // Sort: content matches first (have a relevance score), then alphabetically
        collector.sort(Comparator
                .comparingDouble((BookResult r) -> r.relevanceScore() == null ? 0 : -r.relevanceScore())
                .thenComparing(BookResult::fileName));

        return options.maxResults() > 0
                ? collector.subList(0, Math.min(collector.size(), options.maxResults()))
                : collector;
    }

    /**
     * Lists every book file found in {@code rootDir} without any topic filter.
     */
    public List<BookResult> listAllBooks(Path rootDir,
                                         boolean recursive,
                                         int maxResults) throws IOException {
        validateDirectory(rootDir);
        var collector = new ArrayList<BookResult>();
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;

        Files.walkFileTree(rootDir, Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String ext = ContentExtractor.extension(file);
                if (BOOK_EXTENSIONS.contains(ext)) {
                    collector.add(new BookResult(
                            file.getFileName().toString(),
                            file.toAbsolutePath().toString(),
                            ext,
                            attrs.size(),
                            "Listed",
                            null
                    ));
                }
                if (maxResults > 0 && collector.size() >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        collector.sort(Comparator.comparing(BookResult::fileName));
        return maxResults > 0
                ? collector.subList(0, Math.min(collector.size(), maxResults))
                : collector;
    }

    private Optional<BookResult> processFile(Path file, long size,
                                             List<String> keywords,
                                             SearchOptions options) {
        String ext = ContentExtractor.extension(file);
        if (!BOOK_EXTENSIONS.contains(ext)) return Optional.empty();

        String fileName = file.getFileName().toString();
        String fileNameLower = fileName.toLowerCase();

        // ── Phase 1: file-name match ──────────────────────────────────────────
        boolean nameMatch = keywords.stream().anyMatch(fileNameLower::contains);
        if (nameMatch) {
            return Optional.of(BookResult.fileNameMatch(
                    fileName, file.toAbsolutePath().toString(), ext, size));
        }

        // ── Phase 2: content match (optional) ────────────────────────────────
        if (!options.searchContent()) return Optional.empty();

        Optional<String> textOpt = switch (ext) {
            default     -> extractor.extract(file);
        };

        return textOpt.flatMap(text -> {
            String lower = text.toLowerCase();
            int totalWords = lower.split("\\s+").length;
            int hits = keywords.stream()
                    .mapToInt(kw -> countOccurrences(lower, kw))
                    .sum();
            if (hits == 0) return Optional.empty();
            int relevance = totalWords > 0 ?  hits / totalWords : 0;
            return Optional.of(BookResult.contentMatch(
                    fileName, file.toAbsolutePath().toString(), ext, size, relevance,totalWords));

        });
    }

    private static int countOccurrences(String text, String keyword) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }

    private static void validateDirectory(Path dir) {
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException(
                    "Directory does not exist: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(
                    "Path is not a directory: " + dir);
        }
        if (!Files.isReadable(dir)) {
            throw new IllegalArgumentException(
                    "Directory is not readable (check permissions): " + dir);
        }
    }
}
