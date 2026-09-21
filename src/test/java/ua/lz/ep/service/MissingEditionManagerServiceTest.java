package ua.lz.ep.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.payload.ProcessingResult;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;
import ua.lz.ep.payload.ProcessingTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissingEditionManagerServiceTest {

    private final ThreadPoolTaskExecutor executor = testExecutor();
    private final StorageManager storageManager = mock(StorageManager.class);

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void processCorrectionShouldReturnTaskIdImmediatelyAndCompleteInBackground() throws Exception {
        SolrService solrService = mock(SolrService.class);
        PeriodRequest periodRequest = validCorrectionRequest();
        AtomicBoolean enteredProcessing = new AtomicBoolean(false);

        when(solrService.findBrokenEdition(eq(periodRequest), any(ProcessingTask.class))).thenAnswer(invocation -> {
            enteredProcessing.set(true);
            TimeUnit.MILLISECONDS.sleep(150);
            return List.of("edition-1", "edition-2");
        });

        MissingEditionManagerService managerService = new MissingEditionManagerService(solrService, storageManager, executor);

        String taskId = managerService.submitMissingEditionsTask(new ProcessingTask(), periodRequest);

        assertThat(taskId).isNotBlank();
        assertThat(managerService.getTaskStatus(taskId)).isNotNull();
        assertThat(managerService.getTaskStatus(taskId).getStatus()).isIn("PENDING", "RUNNING");

        waitUntil(enteredProcessing::get, 2_000);
        waitUntil(() -> "COMPLETED".equals(managerService.getTaskStatus(taskId).getStatus()), 2_000);

        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        assertThat(taskStatus.getStatus()).isEqualTo("COMPLETED");
        assertThat(taskStatus.getProgress()).isEqualTo(100);
        assertThat(taskStatus.getMessage()).contains("Found 2 problematic items");
        assertThat(taskStatus.isActive()).isFalse();
        assertThat(taskStatus.getStartedAt()).isNotNull();
        assertThat(taskStatus.getCompletedAt()).isNotNull();
        verify(storageManager).storedReportData(any(ProcessingResult.class));
    }

    @Test
    void stopTaskShouldCancelRunningCorrectionTask() throws Exception {
        SolrService solrService = mock(SolrService.class);
        PeriodRequest periodRequest = validCorrectionRequest();
        AtomicBoolean enteredProcessing = new AtomicBoolean(false);

        when(solrService.findBrokenEdition(eq(periodRequest), any(ProcessingTask.class))).thenAnswer(invocation -> {
            enteredProcessing.set(true);
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Cancelled from test");
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Cancelled from test");
                }
            }
        });

        MissingEditionManagerService managerService = new MissingEditionManagerService(solrService, storageManager, executor);

        String taskId = managerService.processCorrection(new ProcessingTask(), periodRequest);
        waitUntil(() -> enteredProcessing.get(), 2_000);

        assertThat(managerService.stopTask(taskId)).isTrue();
        waitUntil(() -> "CANCELLED".equals(managerService.getTaskStatus(taskId).getStatus()), 2_000);

        TaskStatus taskStatus = managerService.getTaskStatus(taskId);
        assertThat(taskStatus.getStatus()).isEqualTo("CANCELLED");
        assertThat(taskStatus.isActive()).isFalse();
        assertThat(taskStatus.getCompletedAt()).isNotNull();
        verify(storageManager, never()).storedReportData(any(ProcessingResult.class));
    }

    @Test
    void findAllShouldReturnPagedTaskStatuses() throws Exception {
        SolrService solrService = mock(SolrService.class);
        PeriodRequest periodRequest = validCorrectionRequest();
        when(solrService.findBrokenEdition(eq(periodRequest), any(ProcessingTask.class))).thenReturn(List.of());

        MissingEditionManagerService managerService = new MissingEditionManagerService(solrService, storageManager, executor);
        managerService.submitMissingEditionsTask(new ProcessingTask(), periodRequest);
        managerService.submitMissingEditionsTask(new ProcessingTask(), periodRequest);

        waitUntil(() -> managerService.findAll(pageRequest(0, 10)).getTotalElements() == 2, 2_000);

        PageTaskStatus<TaskStatus> page = managerService.findAll(pageRequest(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getPage()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Nested
    class ProcessCorrectionStatusesIntegrationTest {

        @Test
        void processCorrectionShouldCoverPendingRunningAndCompletedStatuses() throws Exception {
            SolrService solrService = mock(SolrService.class);
            PeriodRequest periodRequest = validCorrectionRequest();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            when(solrService.findBrokenEdition(eq(periodRequest), any(ProcessingTask.class)))
                    .thenAnswer(invocation -> {
                        started.countDown();
                        release.await(2, TimeUnit.SECONDS);
                        return List.of("edition-1");
                    });

            MissingEditionManagerService managerService = new MissingEditionManagerService(solrService, storageManager, executor);

            String taskId = managerService.submitMissingEditionsTask(new ProcessingTask(), periodRequest);

            TaskStatus initialStatus = managerService.getTaskStatus(taskId);
            assertThat(initialStatus).isNotNull();
            assertThat(initialStatus.getStatus()).isIn("PENDING", "RUNNING");
            assertThat(initialStatus.getProgress()).isEqualTo(0);
            if ("PENDING".equals(initialStatus.getStatus())) {
                assertThat(initialStatus.getStartedAt()).isNull();
                assertThat(initialStatus.getCompletedAt()).isNull();
            } else {
                assertThat(initialStatus.getStartedAt()).isNotNull();
                assertThat(initialStatus.getCompletedAt()).isNull();
            }

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> "RUNNING".equals(managerService.getTaskStatus(taskId).getStatus()), 2_000);

            TaskStatus runningStatus = managerService.getTaskStatus(taskId);
            assertThat(runningStatus.getStatus()).isEqualTo("RUNNING");
            assertThat(runningStatus.isActive()).isTrue();
            assertThat(runningStatus.getStartedAt()).isNotNull();
            assertThat(runningStatus.getCompletedAt()).isNull();

            release.countDown();
            waitUntil(() -> "COMPLETED".equals(managerService.getTaskStatus(taskId).getStatus()), 2_000);
            waitUntil(() -> !managerService.getTaskStatus(taskId).isActive(), 2_000);

            TaskStatus completedStatus = managerService.getTaskStatus(taskId);
            assertThat(completedStatus.getStatus()).isEqualTo("COMPLETED");
            assertThat(completedStatus.getProgress()).isEqualTo(100);
            assertThat(completedStatus.isActive()).isFalse();
            assertThat(completedStatus.getCompletedAt()).isNotNull();
        }

        @Test
        void processCorrectionShouldMarkTaskAsFailedWhenProcessingThrows() throws Exception {
            SolrService solrService = mock(SolrService.class);
            PeriodRequest periodRequest = validCorrectionRequest();

            when(solrService.findBrokenEdition(eq(periodRequest), any(ProcessingTask.class)))
                    .thenThrow(new IllegalStateException("Solr is unavailable"));

            MissingEditionManagerService managerService = new MissingEditionManagerService(solrService, storageManager, executor);

            String taskId = managerService.submitMissingEditionsTask(new ProcessingTask(), periodRequest);

            waitUntil(() -> "FAILED".equals(managerService.getTaskStatus(taskId).getStatus()), 2_000);

            TaskStatus failedStatus = managerService.getTaskStatus(taskId);
            assertThat(failedStatus.getStatus()).isEqualTo("FAILED");
            assertThat(failedStatus.getMessage()).contains("Solr is unavailable");
            assertThat(failedStatus.isActive()).isFalse();
            assertThat(failedStatus.getStartedAt()).isNotNull();
            assertThat(failedStatus.getCompletedAt()).isNotNull();
            verify(storageManager, never()).storedReportData(any(ProcessingResult.class));
        }
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("Condition was not met within timeout");
    }

    private ThreadPoolTaskExecutor testExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(1);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.setWaitForTasksToCompleteOnShutdown(false);
        taskExecutor.initialize();
        return taskExecutor;
    }

    private PageRequest pageRequest(int page, int size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setSize(size);
        return pageRequest;
    }

    private PeriodRequest validCorrectionRequest() {
        PeriodRequest periodRequest = new PeriodRequest();
        periodRequest.setStartPeriod(LocalDateTime.now().minusHours(1));
        periodRequest.setEndPeriod(LocalDateTime.now());
        periodRequest.setCorrectionType(ua.lz.ep.payload.enums.CorrectionType.ONLY_DOCUMENT_ID);
        return periodRequest;
    }
}
