package ua.lz.ep.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import ua.lz.ep.dto.PeriodRequest;
import ua.lz.ep.dto.request.PageRequest;
import ua.lz.ep.dto.response.PageTaskStatus;
import ua.lz.ep.dto.response.TaskStatus;

@Hidden
public interface LegacyCorrectionApi {

    ResponseEntity<String> startProcessing(@Valid PeriodRequest periodRequest);

    ResponseEntity<?> getTaskStatus(String taskId, HttpServletRequest request);

    ResponseEntity<?> stopTask(String taskId, HttpServletRequest request);

    ResponseEntity<PageTaskStatus<TaskStatus>> getTasks(PageRequest pageable);
}
