package org.camunda.bpm.getstarted.highlight.repository;

import org.camunda.bpm.getstarted.highlight.entity.CamundaHighlightRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CamundaHighlightRecordRepository
        extends JpaRepository<CamundaHighlightRecord, Long> {

    List<CamundaHighlightRecord> findByProcessInstanceIdOrderByIdAsc(String processInstanceId);

    Optional<CamundaHighlightRecord> findFirstByProcessInstanceIdAndElementIdAndEventTypeOrderByIdDesc(
            String processInstanceId, String elementId, String eventType);

    boolean existsByProcessInstanceIdAndElementIdAndEventType(
            String processInstanceId, String elementId, String eventType);
}