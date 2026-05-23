package com.hp.mcpserver.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookResult(

        String fileName,
        String absolutePath,
        String format,
        long sizeBytes,
        String matchReason,
        Double relevanceScore
) {

    /** Convenience builder for a filename-only match. */
    public static BookResult fileNameMatch(String fileName, String absolutePath,
                                           String format, long sizeBytes) {
        return new BookResult(fileName, absolutePath, format, sizeBytes,
                "Topic keyword found in file name", null);
    }

    /** Convenience builder for a content match (PDF / TXT). */
    public static BookResult contentMatch(String fileName, String absolutePath,
                                          String format, long sizeBytes,
                                          int hits,int totalWords){
        double score = totalWords > 0 ? Math.min(1.0, (double) hits / totalWords) : 0.0;
        String reason = String.format("Topic keyword found %d time(s) in file content", hits);
        return new BookResult(fileName, absolutePath, format, sizeBytes, reason,
                Math.round(score * 1000.0) / 1000.0);
    }
}

