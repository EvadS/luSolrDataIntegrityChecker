package ua.lz.ep.Initializer;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ua.lz.ep.config.StorageSettings;
import ua.lz.ep.utils.DirectoryUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

@Log4j2
@Component
public class DirectoryInitializer implements ApplicationRunner {

    @Autowired
    private StorageSettings storageSettings;

    @Override
    public void run(ApplicationArguments args) {
        Path path = Paths.get(storageSettings.getReportsDir());
        DirectoryUtil.createDirectories(path);

        log.info("Yeap. Im running....");
    }
}
