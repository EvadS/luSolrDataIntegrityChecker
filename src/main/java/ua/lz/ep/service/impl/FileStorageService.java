package ua.lz.ep.service.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import ua.lz.ep.config.StorageSettings;
import ua.lz.ep.dto.payload.ProcessingResult;
import ua.lz.ep.service.StorageManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import java.util.stream.Collectors;

@Log4j2
@Service
public class FileStorageService implements StorageManager {
    @Autowired
    private Environment environment;

    // todo:
    @Autowired
    private StorageSettings storageSettings;

    // todo:
    @Override
    public void storedReportData(ProcessingResult processingResult) {
        String[] activeProfiles = environment.getActiveProfiles();

        // Если нет активных профилей — используем "default" чтобы путь был осмысленным
        String profilesPart = activeProfiles.length > 0
                ? String.join("_", activeProfiles)
                : "default";

        // адрес дочерней директории для профиля
        Path childPath = Paths.get(storageSettings.getReportsDir(), profilesPart);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss");
        String fileName = String.format("lost_editions_%s.txt", fmt.format(processingResult.getProcessingStartTime()));
        Path filePath = childPath.resolve(fileName);

        try {
            // дочерняя директория для профиля
            Files.createDirectories(childPath);

            // непосредственно файл
            Files.createFile(filePath); // если нужен именно явный create

        } catch (IOException e) {
            log.error("Unable to create directories {}", childPath, e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
            for (String item: processingResult.getLostEditions()){
                writer.write(item + "\n");
            }
        }catch (IOException e){
            log.error("Unable to save result to file {}", filePath, e);
        }
    }
}
