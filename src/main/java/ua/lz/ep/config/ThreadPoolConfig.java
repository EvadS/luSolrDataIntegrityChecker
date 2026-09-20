package ua.lz.ep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "correctionTaskExecutor")
    public ThreadPoolTaskExecutor correctionTaskExecutor(ua.lz.ep.config.CorrectionProperties correctionProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int maxThreads = Math.max(1, correctionProperties.getMaxThreads());
        executor.setCorePoolSize(maxThreads);
        executor.setMaxPoolSize(maxThreads);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("CorrectionThread-");
        executor.initialize();
        return executor;
    }
}
