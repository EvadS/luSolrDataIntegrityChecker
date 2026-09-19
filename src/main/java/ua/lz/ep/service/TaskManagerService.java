package ua.lz.ep.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;
import ua.lz.ep.payload.ProcessingTask;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Log4j2
@Service
public class TaskManagerService {

    private final SolrService solrService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Map<String, Future<?>> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, ProcessingTask> taskRegistry = new ConcurrentHashMap<>();

    public TaskManagerService(
            SolrService solrService,
            @Qualifier("correctionTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.solrService = solrService;
        this.taskExecutor = taskExecutor;
    }

    public String startTask(String taskId, Runnable taskLogic) {
        ProcessingTask processingTask = taskRegistry.computeIfAbsent(taskId, key -> new ProcessingTask());
        processingTask.initialize(taskId, "Task queued for execution");
        submitTask(processingTask, taskLogic, "Task completed successfully");
        return taskId;
    }

    public boolean stopTask(String taskId) {
        ProcessingTask task = taskRegistry.get(taskId);
        if (task == null) {
            return false;
        }

        Future<?> future = activeTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                task.markCancelled("Task cancelled by request");
                activeTasks.remove(taskId);
            }
            return cancelled;
        }
        return false;
    }

    public String processCorrection(ProcessingTask processingTask, PeriodRequest periodRequest) {
        String taskId = resolveTaskId(processingTask);
        processingTask.initialize(taskId, "Correction task queued for execution");
        taskRegistry.put(taskId, processingTask);

        log.debug("Creating processing task {} for correction request: {}", taskId, periodRequest);

        submitTask(processingTask, () -> {
            List<String> brokenEditions = solrService.findBrokenEdition(periodRequest);
            int brokenCount = brokenEditions == null ? 0 : brokenEditions.size();
            processingTask.updateProgress(100, "Correction finished. Found " + brokenCount + " problematic items.");
        }, "Correction completed successfully");

        return taskId;
    }

    public TaskStatus getTaskStatus(String taskId) {
        ProcessingTask task = taskRegistry.get(taskId);
        if (task == null) {
            return null;
        }

        return toTaskStatus(task);
    }

    public PageTaskStatus<TaskStatus> findAll(PageRequest pageable) {
        int page = pageable == null ? 0 : Math.max(pageable.getPage(), 0);
        int size = pageable == null || pageable.getSize() <= 0 ? 20 : pageable.getSize();
        Comparator<ProcessingTask> comparator = resolveSortComparator(pageable == null ? null : pageable.getSort());

        List<TaskStatus> tasks = taskRegistry.values().stream()
                .sorted(comparator)
                .map(this::toTaskStatus)
                .toList();

        int fromIndex = Math.min(page * size, tasks.size());
        int toIndex = Math.min(fromIndex + size, tasks.size());
        int totalPages = tasks.isEmpty() ? 0 : (int) Math.ceil((double) tasks.size() / size);

        return new PageTaskStatus<>(tasks.subList(fromIndex, toIndex), page, size, tasks.size(), totalPages);
    }

    private Comparator<ProcessingTask> resolveSortComparator(String sortExpression) {
        String normalizedSort = sortExpression == null || sortExpression.isBlank()
                ? "createdAt,desc"
                : sortExpression.trim();

        String[] sortParts = normalizedSort.split(",", 2);
        String sortField = sortParts[0].trim();
        boolean descending = sortParts.length < 2 || !"asc".equalsIgnoreCase(sortParts[1].trim());

        Comparator<ProcessingTask> comparator = switch (sortField) {
            case "taskId", "id" -> Comparator.comparing(ProcessingTask::getId, Comparator.nullsLast(String::compareToIgnoreCase));
            case "startedAt" -> Comparator.comparing(ProcessingTask::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "completedAt" -> Comparator.comparing(ProcessingTask::getCompletedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(task -> task.getStatus().name(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "progress" -> Comparator.comparing(ProcessingTask::getProgress, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(ProcessingTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(ProcessingTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if (descending) {
            comparator = comparator.reversed();
        }

        return comparator.thenComparing(ProcessingTask::getId, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private void submitTask(ProcessingTask processingTask, Runnable taskLogic, String successMessage) {
        Runnable wrappedTask = () -> {
            processingTask.markRunning("Task is running");

            try {
                taskLogic.run();

                if (Thread.currentThread().isInterrupted()) {
                    processingTask.markCancelled("Task cancelled by interruption");
                    Thread.currentThread().interrupt();
                    return;
                }

                processingTask.markCompleted(resolveSuccessMessage(processingTask, successMessage));
            } catch (CancellationException e) {
                Thread.currentThread().interrupt();
                processingTask.markCancelled(resolveErrorMessage(e, "Task cancelled"));
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    processingTask.markCancelled(resolveErrorMessage(e, "Task cancelled"));
                } else {
                    log.error("Task {} failed", processingTask.getId(), e);
                    processingTask.markFailed(resolveErrorMessage(e, "Task failed"));
                }
            } finally {
                activeTasks.remove(processingTask.getId());
            }
        };

        Future<?> future = taskExecutor.submit(wrappedTask);
        activeTasks.put(processingTask.getId(), future);
    }

    private String resolveTaskId(ProcessingTask processingTask) {
        if (processingTask.getId() != null && !processingTask.getId().isBlank()) {
            return processingTask.getId();
        }
        return UUID.randomUUID().toString();
    }

    private TaskStatus toTaskStatus(ProcessingTask task) {
        return new TaskStatus(
                task.getId(),
                task.getStatus().name(),
                task.getProgress(),
                task.getMessage(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                activeTasks.containsKey(task.getId()));
    }

    private String resolveSuccessMessage(ProcessingTask processingTask, String defaultMessage) {
        String message = processingTask.getMessage();
        return message == null || message.isBlank() ? defaultMessage : message;
    }

    private String resolveErrorMessage(Exception exception, String defaultMessage) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}
