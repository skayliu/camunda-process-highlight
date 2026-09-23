package org.camunda.bpm.getstarted.highlight.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "camunda_highlight_record")
public class CamundaHighlightRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(name = "process_definition_id", nullable = false, length = 128)
    private String processDefinitionId;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(name = "element_id", nullable = false, length = 64)
    private String elementId;

    @Column(name = "element_name", length = 255)
    private String elementName;

    /** TASK/GATEWAY/START_EVENT/END_EVENT/FLOW/SUBPROCESS/OTHER */
    @Column(name = "element_type", nullable = false, length = 32)
    private String elementType;

    /** start/end/take */
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "assignee", length = 64)
    private String assignee;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String v) { this.processInstanceId = v; }
    public String getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(String v) { this.processDefinitionId = v; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String v) { this.executionId = v; }
    public String getElementId() { return elementId; }
    public void setElementId(String v) { this.elementId = v; }
    public String getElementName() { return elementName; }
    public void setElementName(String v) { this.elementName = v; }
    public String getElementType() { return elementType; }
    public void setElementType(String v) { this.elementType = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime v) { this.startTime = v; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime v) { this.endTime = v; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String v) { this.assignee = v; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
}