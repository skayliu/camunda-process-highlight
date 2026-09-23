package org.camunda.bpm.getstarted.highlight.listener;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.getstarted.highlight.constant.HighlightConstants;
import org.camunda.bpm.getstarted.highlight.entity.CamundaHighlightRecord;
import org.camunda.bpm.getstarted.highlight.repository.CamundaHighlightRecordRepository;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class GlobalHighlightRecordListener {

    private static final Logger log = LoggerFactory.getLogger(GlobalHighlightRecordListener.class);

    private final CamundaHighlightRecordRepository repository;
    private final RepositoryService repositoryService;

    public GlobalHighlightRecordListener(CamundaHighlightRecordRepository repository,
                                         RepositoryService repositoryService) {
        this.repository = repository;
        this.repositoryService = repositoryService;
    }

    /* ============ ExecutionListener：处理节点的 start/end 和连线的 take ============ */


    @Transactional(propagation = Propagation.REQUIRED)
    @EventListener
    public void onExecutionEvent(DelegateExecution execution) {
        String eventName = execution.getEventName();
        String instanceId = execution.getProcessInstanceId();
        String defId      = execution.getProcessDefinitionId();

        if (instanceId == null || defId == null) {
            return;
        }

        try {
            switch (eventName) {
                case "take": {
                    // ★ 关键修正：take 事件必须用 getCurrentTransitionId() 拿连线 ID
                    String flowId = execution.getCurrentTransitionId();
                    if (flowId == null) return;
                    handleTake(execution, instanceId, defId, flowId);
                    break;
                }
                case "start": {
                    String elementId = execution.getCurrentActivityId();
                    if (elementId == null) return;  // 流程实例级 start 时可能为 null
                    handleStart(execution, instanceId, defId, elementId);
                    break;
                }
                case "end": {
                    String elementId = execution.getCurrentActivityId();
                    if (elementId == null) return;
                    handleEnd(instanceId, elementId);
                    break;
                }
                default:
                    // 其他事件忽略
            }
        } catch (Exception e) {
            log.warn("记录高亮事件失败: instance={}, event={}", instanceId, eventName, e);
        }
    }

    private void handleTake(DelegateExecution execution, String instanceId,
                            String defId, String flowId) {
        if (repository.existsByProcessInstanceIdAndElementIdAndEventType(
                instanceId, flowId, HighlightConstants.EVENT_TAKE)) {
            return; // 同一条连线只记录一次“走过”
        }
        CamundaHighlightRecord record = new CamundaHighlightRecord();
        record.setProcessInstanceId(instanceId);
        record.setProcessDefinitionId(defId);
        record.setExecutionId(execution.getId());
        record.setElementId(flowId);
        record.setElementName(resolveElementName(defId, flowId));
        record.setElementType(HighlightConstants.ELEMENT_FLOW);
        record.setEventType(HighlightConstants.EVENT_TAKE);
        record.setCreateTime(LocalDateTime.now());
        repository.save(record);
    }

    private void handleStart(DelegateExecution execution, String instanceId,
                             String defId, String elementId) {
        // 幂等：同一实例 + 同一节点 + start 事件只记一次
        if (repository.existsByProcessInstanceIdAndElementIdAndEventType(
                instanceId, elementId, HighlightConstants.EVENT_START)) {
            return;
        }
        CamundaHighlightRecord record = new CamundaHighlightRecord();
        record.setProcessInstanceId(instanceId);
        record.setProcessDefinitionId(defId);
        record.setExecutionId(execution.getId());
        record.setElementId(elementId);
        record.setElementName(resolveElementName(defId, elementId));
        record.setElementType(resolveElementType(defId, elementId));
        record.setEventType(HighlightConstants.EVENT_START);
        record.setStartTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        repository.save(record);
    }

    private void handleEnd(String instanceId, String elementId) {
        repository.findFirstByProcessInstanceIdAndElementIdAndEventTypeOrderByIdDesc(
                        instanceId, elementId, HighlightConstants.EVENT_START)
                .ifPresent(record -> {
                    record.setEndTime(LocalDateTime.now());
                    repository.save(record);
                });
    }

    /* ============ TaskListener：补 assignee ============ */


    @Transactional(propagation = Propagation.REQUIRED)
    @EventListener
    public void onExecutionEvent(DelegateTask delegateTask) {

        String eventName = delegateTask.getEventName();
        if (!"create".equals(eventName) && !"assignment".equals(eventName)) {
            return;
        }
        String assignee = delegateTask.getAssignee();
        if (assignee == null) {
            return;
        }
        try {
            repository.findFirstByProcessInstanceIdAndElementIdAndEventTypeOrderByIdDesc(
                            delegateTask.getProcessInstanceId(),
                            delegateTask.getTaskDefinitionKey(),
                            HighlightConstants.EVENT_START)
                    .ifPresent(record -> {
                        record.setAssignee(assignee);
                        repository.save(record);
                    });
        } catch (Exception e) {
            log.warn("更新 assignee 失败: task={}", delegateTask.getId(), e);
        }
    }

    /* ============ 从 BPMN 模型解析元素名称和类型 ============ */

    private String resolveElementName(String defId, String elementId) {
        FlowElement el = loadFlowElement(defId, elementId);
        return el != null ? el.getName() : null;
    }

    private String resolveElementType(String defId, String elementId) {
        FlowElement el = loadFlowElement(defId, elementId);
        if (el == null) return HighlightConstants.ELEMENT_OTHER;

        if (el instanceof SequenceFlow)  return HighlightConstants.ELEMENT_FLOW;
        if (el instanceof Gateway)       return HighlightConstants.ELEMENT_GATEWAY;
        if (el instanceof StartEvent)    return HighlightConstants.ELEMENT_START_EVENT;
        if (el instanceof EndEvent)      return HighlightConstants.ELEMENT_END_EVENT;
        if (el instanceof SubProcess)    return HighlightConstants.ELEMENT_SUBPROCESS;
        if (el instanceof Task)          return HighlightConstants.ELEMENT_TASK;
        if (el instanceof Activity)      return HighlightConstants.ELEMENT_TASK;
        return HighlightConstants.ELEMENT_OTHER;
    }

    private FlowElement loadFlowElement(String defId, String elementId) {
        try {
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(defId);
            return model.getModelElementById(elementId);
        } catch (Exception e) {
            return null;
        }
    }
}