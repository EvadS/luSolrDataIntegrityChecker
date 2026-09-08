package ua.lz.ep.service;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import ua.lz.ep.dto.CorrectionRequest;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.utils.SolrUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Service
public class TaskManagerService {

    @Autowired
    private SolrService solrService;

    private final ThreadPoolTaskExecutor taskExecutor;
    private final Map<String, Future<?>> activeTasks = new ConcurrentHashMap<>();

    public TaskManagerService(@Qualifier("correctionTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public String startTask(String taskId, Runnable taskLogic) {
        // Wrap the logic to clean up the map once completed
        Runnable wrappedTask = () -> {
            try {
                taskLogic.run();
            } finally {
                activeTasks.remove(taskId);
            }
        };

        Future<?> future = taskExecutor.submit(wrappedTask);
        activeTasks.put(taskId, future);
        return taskId;
    }

    public boolean stopTask(String taskId) {
        Future<?> future = activeTasks.get(taskId);
        if (future != null) {
            // true means interrupt the running thread if necessary
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                activeTasks.remove(taskId);
            }
            return cancelled;
        }
        return false;
    }

    public List<String> getActiveTaskIds() {
        return activeTasks.keySet().stream().toList();
    }


    public String processCorrection(ProcessingTask processingTask, CorrectionRequest correctionRequest) {
        // создать запись
        Long id  = 1L;
        try {

            // todo: for tests
            solrService.correctionProcessing(correctionRequest);

        } catch (Exception e) {
         // Handle exception, log it, etc.;
            e.printStackTrace();
        }

        return id.toString();
    }
}
