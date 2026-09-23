package ua.lz.ep.utils;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class DirectoryUtil {

    private DirectoryUtil() {
    }

    public static void createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Successfully created directory at: {}", path.toAbsolutePath());
            } else {
                log.info("Directory already exists at: {}", path.toAbsolutePath());
            }
        } catch (SecurityException e) {
            log.error("Permission denied: Cannot create directory at {}", path.toAbsolutePath(), e);
            // Throwing a runtime exception stops application startup if the directory is mandatory
            throw new IllegalStateException("Failed to create mandatory startup directory: " + path, e);
        } catch (AccessDeniedException e) {
            log.error("Access denied: You do not have permission to create the directory", e);
            // Throwing a runtime exception stops application startup if the directory is mandatory
            throw new IllegalStateException("Failed to create mandatory startup directory: " + path, e);
        }
        catch (IOException e) {
            log.error("I/O error while creating directory at {}", path.toAbsolutePath(), e);
            throw new IllegalStateException("Failed to initialize storage directory", e);
        }
    }
}
