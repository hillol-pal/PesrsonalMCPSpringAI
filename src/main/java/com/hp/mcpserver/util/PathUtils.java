package com.hp.mcpserver.util;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtils {

    private PathUtils() {}

    /**
     * Resolves a raw path string entered by a user into an absolute {@link Path}.
     *
     * <ul>
     *   <li>{@code ~} is expanded to the current user's home directory.</li>
     *   <li>Forward-slashes are used on all platforms for normalisation.</li>
     *   <li>Trailing separators are stripped.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the string cannot be parsed as a path.
     */
    public static Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank.");
        }

        String trimmed = rawPath.strip();

        // Expand home-directory shorthand (~, ~/..., ~\...)
        if (trimmed.equals("~")) {
            return homeDir();
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            trimmed = homeDir().toString() + trimmed.substring(1);
        }

        // Replace Windows back-slashes with the OS separator so that
        // "C:\Users\Alice" works on Windows and is still parseable on Unix
        // (Unix treats '\' as a literal character in file names – unlikely
        // for user-typed paths but we normalise anyway).
        trimmed = trimmed.replace('\\', File.separatorChar);

        try {
            Path p = Paths.get(trimmed).toAbsolutePath().normalize();
            return p;
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                    "Cannot resolve path '%s': %s".formatted(rawPath, e.getReason()), e);
        }
    }

    private static Path homeDir() {
        return Paths.get(System.getProperty("user.home"));
    }
}
