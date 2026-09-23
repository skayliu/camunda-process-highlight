package org.camunda.bpm.getstarted.highlight.service;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.getstarted.highlight.constant.HighlightConstants;
import org.camunda.bpm.getstarted.highlight.entity.CamundaHighlightRecord;
import org.camunda.bpm.getstarted.highlight.repository.CamundaHighlightRecordRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcessHighlightService {

    private final CamundaHighlightRecordRepository repository;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public ProcessHighlightService(CamundaHighlightRecordRepository repository,
                                   RuntimeService runtimeService,
                                   HistoryService historyService) {
        this.repository = repository;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    public HighlightResult getHighlightInfo(String processInstanceId) {
        HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();

        List<CamundaHighlightRecord> records =
                repository.findByProcessInstanceIdOrderByIdAsc(processInstanceId);

        Set<String> executedNodes = new LinkedHashSet<>();
        Set<String> executedFlows = new LinkedHashSet<>();
        Map<String, List<CamundaHighlightRecord>> detail = new LinkedHashMap<>();

        for (CamundaHighlightRecord r : records) {
            detail.computeIfAbsent(r.getElementId(), k -> new ArrayList<>()).add(r);

            if (HighlightConstants.ELEMENT_FLOW.equals(r.getElementType())) {
                executedFlows.add(r.getElementId());
            } else {
                executedNodes.add(r.getElementId());
            }
        }

        List<String> currentNodes = Collections.emptyList();
        if (processInstance !=null && processInstance.getEndTime() == null) {
                currentNodes = runtimeService
                        .getActiveActivityIds(processInstanceId);
        }

        HighlightResult result = new HighlightResult();
        result.setExecutedNodeIds(new ArrayList<>(executedNodes));
        result.setExecutedSequenceFlowIds(new ArrayList<>(executedFlows));
        result.setCurrentActivityIds(currentNodes);
        result.setRecords(records);   // 明细可选返回
        return result;
    }

    public static class HighlightResult {
        private List<String> executedNodeIds;
        private List<String> executedSequenceFlowIds;
        private List<String> currentActivityIds;
        private List<CamundaHighlightRecord> records;

        public List<String> getExecutedNodeIds() { return executedNodeIds; }
        public void setExecutedNodeIds(List<String> v) { this.executedNodeIds = v; }
        public List<String> getExecutedSequenceFlowIds() { return executedSequenceFlowIds; }
        public void setExecutedSequenceFlowIds(List<String> v) { this.executedSequenceFlowIds = v; }
        public List<String> getCurrentActivityIds() { return currentActivityIds; }
        public void setCurrentActivityIds(List<String> v) { this.currentActivityIds = v; }
        public List<CamundaHighlightRecord> getRecords() { return records; }
        public void setRecords(List<CamundaHighlightRecord> v) { this.records = v; }
    }
}