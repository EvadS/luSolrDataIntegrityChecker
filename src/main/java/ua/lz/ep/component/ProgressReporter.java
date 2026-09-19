package ua.lz.ep.component;

import org.springframework.stereotype.Component;
import ua.lz.ep.aop.ReportProgress;

@Component
public class ProgressReporter {

    @ReportProgress
    public void reportProgress(int processed, long total, int pageSize) {
        // пустое тело — аспект выполнит логирование
    }
}
