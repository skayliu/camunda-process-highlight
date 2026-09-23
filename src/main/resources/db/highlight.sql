CREATE TABLE IF NOT EXISTS `camunda_highlight_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例ID',
    `process_definition_id` varchar(128) NOT NULL COMMENT '流程定义ID',
    `execution_id` varchar(64) DEFAULT NULL COMMENT '执行实例ID',
    `element_id` varchar(64) NOT NULL COMMENT '元素ID（节点/连线ID）',
    `element_name` varchar(255) DEFAULT NULL COMMENT '元素名称',
    `element_type` varchar(32) NOT NULL COMMENT '元素类型：TASK/GATEWAY/START_EVENT/END_EVENT/FLOW',
    `event_type` varchar(16) NOT NULL COMMENT '事件类型：start/end/take',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    `assignee` varchar(64) DEFAULT NULL COMMENT '处理人',
    `create_time` datetime NOT NULL COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_element` (`element_id`)
    )