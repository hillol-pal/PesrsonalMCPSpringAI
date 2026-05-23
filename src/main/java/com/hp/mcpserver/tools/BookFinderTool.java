package com.hp.mcpserver.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hp.mcpserver.models.BookResult;
import com.hp.mcpserver.models.SearchOptions;
import com.hp.mcpserver.service.BookSearchService;
import com.hp.mcpserver.util.PathUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookFinderTool {

    private final BookSearchService searchService;
    private final ObjectMapper mapper;

    public BookFinderTool(BookSearchService searchService) {
        this.searchService = searchService;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }


    /**
     * Searches for book files related to a topic inside a given directory.
     *
     * <p>Checks file names.PDF content is <em>not</em>
     * extracted for this fast variant. Use {@link #findBooksDeep} for a
     * full-content scan.
     *
     * @param directoryPath absolute or home-relative path to the directory.
     *                      Examples: {@code /Users/alice/Books},
     *                      {@code ~/Books}, {@code C:\Users\Alice\Books}.
     * @param topic         topic or keyword(s) to search for, separated by spaces
     *                      (e.g. {@code "machine learning"} or {@code "java spring"}).
     * @param recursive     set to {@code true} to include sub-directories
     *                      (default: {@code true}).
     * @return JSON string with matching book results or an error message.
     */
    @Tool(name = "find_books_by_topic",
            description = """
                  Search for book files (PDF,  TXT) related to a topic
                  inside a directory. Checks file names.
                  Works on macOS, Linux and Windows paths.
                  Returns a JSON array of matched books with file name, path, format,
                  size and the reason they matched.
                  """)
    public String findBooksByTopic(
            @ToolParam(description =
                    "Absolute or home-relative path to the directory to search. " +
                            "Examples: /Users/alice/Books, ~/Books, C:\\Users\\Alice\\Books")
            String directoryPath,

            @ToolParam(description =
                    "Topic or keyword(s) to look for, space-separated. " +
                            "Example: 'machine learning' or 'java spring boot'")
            String topic,

            @ToolParam(description =
                    "Whether to search sub-directories recursively (true/false, default true)",
                    required = false)
            Boolean recursive
    ) {
        try {
            Path dir = PathUtils.resolve(directoryPath);
            List<String> keywords = splitKeywords(topic);
            boolean recurse = recursive == null || recursive;

            SearchOptions opts = new SearchOptions(recurse, true, 5, 50);
            List<BookResult> results = searchService.findBooks(dir, keywords, opts);

            return formatResults(results, topic, dir.toString());
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("IO error while scanning directory: " + e.getMessage());
        }
    }

    /**
     * Lists all book files found in a directory without any topic filter.
     *
     * <p>Useful to first understand what's available before narrowing by topic.
     *
     * @param directoryPath path to the directory.
     * @param recursive     include sub-directories.
     * @param maxResults    maximum number of files to return (0 = unlimited, max 200).
     * @return JSON string with all discovered book files.
     */
    @Tool(name = "list_all_books",
            description = """
                  Lists every book file (PDF, ePub, MOBI, TXT …) found in a directory
                  without any topic filter. Useful for exploring what is available.
                  Works on macOS, Linux and Windows paths.
                  """)
    public String listAllBooks(
            @ToolParam(description =
                    "Absolute or home-relative path to the directory to scan. " +
                            "Examples: /home/user/Books, ~/Documents, C:\\Books")
            String directoryPath,

            @ToolParam(description =
                    "Whether to include sub-directories (true/false, default true)",
                    required = false)
            Boolean recursive,

            @ToolParam(description =
                    "Maximum number of results to return. 0 means no limit (capped at 200). Default: 100",
                    required = false)
            Integer maxResults
    ) {
        try {
            Path dir = PathUtils.resolve(directoryPath);
            boolean recurse = recursive == null || recursive;
            int limit = maxResults == null ? 100 : Math.min(maxResults, 200);

            List<BookResult> results = searchService.listAllBooks(dir, recurse, limit);

            if (results.isEmpty()) {
                return toJson(Map.of(
                        "directory", dir.toString(),
                        "totalFound", 0,
                        "message", "No book files found in the directory.",
                        "supportedFormats", BookSearchService.BOOK_EXTENSIONS
                ));
            }

            // Group by format for a useful summary
            Map<String, Long> byFormat = results.stream()
                    .collect(Collectors.groupingBy(BookResult::format, Collectors.counting()));

            return toJson(Map.of(
                    "directory", dir.toString(),
                    "totalFound", results.size(),
                    "byFormat", byFormat,
                    "books", results
            ));
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("IO error while listing directory: " + e.getMessage());
        }
    }


    /**
     * Performs a deep, content-aware search for books related to a topic.
     *
     * <p>Unlike {@link #findBooksByTopic}, this tool also extracts and searches
     * the full text of PDF files (up to 5 pages) and plain-text files.
     * It is slower but finds matches that are not visible in the file name.
     *
     * @param directoryPath path to the directory.
     * @param topic         topic keywords.
     * @param maxDepth      maximum sub-directory depth (1 = current dir only, default 5).
     * @param maxResults    cap on results returned (default 5).
     * @return JSON string with matched books, relevance scores, and match reasons.
     */
    @Tool(name = "find_books_deep",
            description = """
                  Deep, content-aware search that reads the text of PDF and TXT files
                  (up to 5 pages per PDF) in addition to file names and ePub metadata.
                  Slower than find_books_by_topic but finds matches inside file content.
                  Returns relevance scores so you can rank results.
                  Works on macOS, Linux and Windows paths.
                  """)
    public String findBooksDeep(
            @ToolParam(description =
                    "Absolute or home-relative path to the directory to scan.")
            String directoryPath,

            @ToolParam(description =
                    "Topic or keyword(s) to search for inside the book content. " +
                            "Space-separated; all keywords are checked together.")
            String topic,

            @ToolParam(description =
                    "Maximum directory recursion depth (1 = current directory only, " +
                            "default 5, maximum 20)",
                    required = false)
            Integer maxDepth,

            @ToolParam(description =
                    "Maximum number of results to return (default 30, maximum 100)",
                    required = false)
            Integer maxResults
    ) {
        try {
            Path dir = PathUtils.resolve(directoryPath);
            List<String> keywords = splitKeywords(topic);
            int depth   = clamp(maxDepth,   1,  20, 5);
            int limit   = clamp(maxResults,  1, 100, 30);

            SearchOptions opts = new SearchOptions(true, true, depth, limit);
            List<BookResult> results = searchService.findBooks(dir, keywords, opts);

            return formatResults(results, topic, dir.toString());
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("IO error during deep scan: " + e.getMessage());
        }
    }


    private String formatResults(List<BookResult> results, String topic, String dir) {
        if (results.isEmpty()) {
            return toJson(Map.of(
                    "directory", dir,
                    "topic", topic,
                    "totalFound", 0,
                    "message", "No books found matching the topic '" + topic + "'. " +
                            "Try broader keywords or enable deep content search."
            ));
        }
        return toJson(Map.of(
                "directory", dir,
                "topic", topic,
                "totalFound", results.size(),
                "books", results
        ));
    }

    private List<String> splitKeywords(String topic) {
        return Arrays.stream(topic.split("[\\s,;]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Serialisation failure: " + e.getMessage() + "\"}";
        }
    }

    private String errorJson(String message) {
        return toJson(Map.of("error", message));
    }

    private static int clamp(Integer value, int min, int max, int defaultVal) {
        if (value == null) return defaultVal;
        return Math.max(min, Math.min(max, value));
    }
}
