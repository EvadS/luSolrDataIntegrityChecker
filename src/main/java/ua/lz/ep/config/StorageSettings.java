package ua.lz.ep.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Getter
@Log4j2
@Configuration
public class StorageSettings {

    @Value("${data.integrity.reports.dir:${user.dir}/reports}")
    private String reportsDir;

    @PostConstruct
    public void init(){
        log.info("Reports directory set to: {}", reportsDir);
    }
}
