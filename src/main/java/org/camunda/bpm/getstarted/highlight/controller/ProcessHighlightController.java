package org.camunda.bpm.getstarted.highlight.controller;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.getstarted.highlight.service.ProcessHighlightService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/process")
public class ProcessHighlightController {

    private final ProcessHighlightService highlightService;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    public ProcessHighlightController(ProcessHighlightService highlightService,
                                      RepositoryService repositoryService,
                                      RuntimeService runtimeService) {
        this.highlightService = highlightService;
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
    }

    @GetMapping("/{processInstanceId}/highlight")
    public ProcessHighlightService.HighlightResult getHighlight(
            @PathVariable("processInstanceId") String processInstanceId) {
        return highlightService.getHighlightInfo(processInstanceId);
    }

    @GetMapping(value = "/bpmn-xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getBpmnXml(
            @RequestParam("processDefinitionId") String processDefinitionId) {
        try (InputStream in = repositoryService.getProcessModel(processDefinitionId)) {
            if (in == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("<error>找不到流程定义: " + processDefinitionId + "</error>");
            }
            return ResponseEntity.ok(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<error>" + e.getMessage() + "</error>");
        }
    }

    @GetMapping("/default-instance")
    public Map<String, String> getDefaultInstance() {
        var instance = runtimeService.createProcessInstanceQuery()
                .orderByProcessInstanceId().desc().list().stream()
                .findFirst().orElse(null);
        Map<String, String> r = new HashMap<>();
        if (instance != null) {
            r.put("instanceId", instance.getId());
            r.put("processDefinitionId", instance.getProcessDefinitionId());
        }
        return r;
    }
}