package ua.lz.ep.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.lz.ep.dto.CorrectionRequest;
import ua.lz.ep.payload.ProcessingTask;
import ua.lz.ep.service.TaskManagerService;


@RestController
@RequestMapping("/api/correction")
public class CorrectionController {

    @Autowired
    private TaskManagerService managerService;

    @PostMapping("/start-processing")
    public ResponseEntity<String> startReport(@RequestBody CorrectionRequest correctionRequest) {
        ProcessingTask processingTask = new ProcessingTask();
        String id = managerService.processCorrection(processingTask, correctionRequest);
        return ResponseEntity.ok(id);
    }
}
