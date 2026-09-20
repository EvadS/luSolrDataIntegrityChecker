package ua.lz.ep.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void createDirectoriesShouldCreateMissingDirectoryTree() {
        Path target = tempDir.resolve("reports").resolve("dev");

        DirectoryUtil.createDirectories(target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    void createDirectoriesShouldKeepExistingDirectory() {
        Path target = tempDir.resolve("reports");
        DirectoryUtil.createDirectories(target);

        DirectoryUtil.createDirectories(target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test
    void createDirectoriesShouldLeaveExistingFileUntouched() throws Exception {
        Path filePath = Files.createFile(tempDir.resolve("not-a-directory.txt"));

        DirectoryUtil.createDirectories(filePath);

        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.isRegularFile(filePath)).isTrue();
    }
}
