package ua.lz.ep.Initializer;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ua.lz.ep.config.StorageSettings;
import ua.lz.ep.config.SolrProperties;
import ua.lz.ep.service.SolrService;
import ua.lz.ep.utils.DirectoryUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

@Log4j2
@Component
public class ApplicationInitializer implements ApplicationRunner {

    @Autowired
    private StorageSettings storageSettings;

    @Autowired
    private SolrService solrService;

    @Autowired
    private SolrProperties solrProperties;

    @Override
    public void run(ApplicationArguments args) {
        Path path = Paths.get(storageSettings.getReportsDir());
        DirectoryUtil.createDirectories(path);

        log.info("Yeap. Im running....");

        // Validate Solr collections connectivity moved out of SolrServiceImpl constructor
        try {
            // ping collection1
            boolean c1 = solrService.pingCollection(solrProperties.getCollection1());
            if (!c1) {
                throw new IllegalStateException("Failed to establish Solr connection for collection1='" + solrProperties.getCollection1() + "'");
            }
            log.info("Solr connection established for collection1='{}'", solrProperties.getCollection1());

            // ping edition collection
            boolean edition = solrService.pingCollection(solrProperties.getEdition());
            if (!edition) {
                throw new IllegalStateException("Failed to establish Solr connection for edition='" + solrProperties.getEdition() + "'");
            }
            log.info("Solr connection established for edition='{}'", solrProperties.getEdition());
        } catch (Exception e) {
            log.error("Solr collection validation failed during application startup", e);
            // Rethrow to fail startup
            throw e;
        }
    }
}
