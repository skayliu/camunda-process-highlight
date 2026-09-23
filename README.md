# Camunda 流程图执行轨迹高亮示例

这是一个基于 **Camunda 7 + Spring Boot 3 + Spring Data JPA + bpmn-js** 的最小可运行示例工程。它在流程执行过程中记录“经过的节点和连线”，并通过浏览器中的 bpmn-js Viewer 将：

- **绿色**：本次流程实例已经执行过的节点和连线；
- **蓝色**：流程当前停留的活动节点；

进行可视化展示。

项目适合用于理解流程轨迹采集、自定义执行审计、给业务门户集成流程图高亮等场景。它不是完整的流程监控平台，也不包含统计报表、权限控制和生产级运维能力。

---

## 1. 技术栈与版本

| 分类 | 技术 |
| --- | --- |
| 流程引擎 | Camunda Platform 7.24.0 |
| 应用框架 | Spring Boot 3.5.5 |
| Java | Java 17 |
| 数据访问 | Spring Data JPA / Hibernate |
| 数据库 | MySQL（当前默认配置）；工程中也保留了 H2 依赖 |
| 前端渲染 | bpmn-js 17.11.1 |
| 流程部署 | Spring Boot Starter 自动部署 BPMN |

> 注意：本项目使用的是 **Camunda Platform 7**（Java 嵌入式引擎），不是 Camunda 8 / Zeebe。

---

## 2. 实现思路

整体方案分成四步：**事件采集 → 轨迹落库 → 聚合查询 → 前端高亮**。

```text
BPMN 流程执行
    │
    │ Camunda Spring Eventing 发布执行/任务事件
    ▼
GlobalHighlightRecordListener
    │ start / end / take / task create|assignment
    ▼
camunda_highlight_record 表
    │
    ▼
ProcessHighlightService 聚合节点、连线和当前活动
    │
    ▼
REST API + bpmn-js canvas marker
    │
    ▼
viewer.html 展示绿色历史轨迹、蓝色当前节点
```

### 2.1 启动示例流程

`ProcessHighlightApplication` 监听 `PostDeployEvent`，应用启动并完成 BPMN 部署后自动启动一个流程实例：

```java
runtimeService.startProcessInstanceByKey("Process_0op232d", Map.of("amount", 100));
```

示例流程会根据 `amount` 走不同分支。默认传入 `100` 时，会进入“起草 → 审批11 → 审批12 → 审批3 → 结束”路径；“审批2”所在分支不会执行。

> 每次应用启动都会创建一个新的示例流程实例，这只是演示行为，生产环境不应这样实现。

### 2.2 全局事件采集

工程没有在每个 BPMN 元素上单独配置 Listener，而是通过 `application.yaml` 开启 Camunda Spring Eventing：

```yaml
camunda.bpm:
  history-level: full
  eventing:
    enabled: true
    execution: true
    history: true
    task: true
```

`GlobalHighlightRecordListener` 使用 Spring `@EventListener` 接收引擎事件：

| 引擎事件 | 处理方式 | 写入内容 |
| --- | --- | --- |
| `ExecutionListener.take` | 通过 `execution.getCurrentTransitionId()` 获取连线 ID | 新增一条 `FLOW / take` 记录 |
| `ExecutionListener.start` | 通过 `execution.getCurrentActivityId()` 获取活动 ID | 新增一条元素 `start` 记录，并保存开始时间 |
| `ExecutionListener.end` | 查找该元素最新一条 `start` 记录 | 更新记录的 `endTime` |
| `TaskListener.create` / `assignment` | 通过 `taskDefinitionKey` 关联活动记录 | 更新处理人 `assignee` |

元素名称和元素类型不是写死在代码里，而是在事件发生时通过 `RepositoryService.getBpmnModelInstance()` 从 BPMN 模型中解析：

- 节点名称：`FlowElement.getName()`
- 节点类型：`Task`、`Gateway`、`StartEvent`、`EndEvent`、`SubProcess` 等

### 2.3 幂等与时间补全

为避免重复点击或重复事件造成脏数据，监听器在写入前检查：

```java
existsByProcessInstanceIdAndElementIdAndEventType(...)
```

同一个流程实例中：

- 某个活动的 `start` 只记录一次；
- 某条连线的 `take` 只记录一次；
- 活动结束时不会新增 `EVENT_END` 记录，而是把已有 `start` 记录的 `endTime` 补上；
- 任务创建或分配处理人时，只更新已有活动记录中的 `assignee`。

也就是说，表中一行通常代表“某实例、某元素的一次执行记录”，而不是独立的开始/结束两条日志。

### 2.4 高亮数据聚合

`ProcessHighlightService.getHighlightInfo()` 读取自定义记录表，并输出三类 ID：

- `executedNodeIds`：执行过的节点；
- `executedSequenceFlowIds`：走过的连线；
- `currentActivityIds`：运行时仍处激活状态的节点。

已结束流程实例不再调用运行时接口获取当前节点，而是返回空数组，避免对已归档实例产生误导。

### 2.5 前端渲染

`viewer.html` 使用 bpmn-js 的 **Navigated Viewer**：

1. 调用 `/api/process/bpmn-xml?processDefinitionId=...` 加载 BPMN XML；
2. 调用 `/api/process/{processInstanceId}/highlight` 获取高亮数据；
3. 使用 `canvas.addMarker(elementId, markerName)` 给元素加标记；
4. 通过 CSS 类实现绿色历史轨迹和蓝色当前节点。

核心样式：

- `.highlight-node`：已执行节点；
- `.highlight-flow`：已执行连线；
- `.current-node`：当前活动节点。

页面会自动调用 `/api/process/default-instance` 获取最新流程实例并渲染，也可以手工输入实例 ID 和流程定义 ID。

---

## 3. 项目结构

```text
camunda-process-highlight/
├── pom.xml
├── src/main/java/org/camunda/bpm/getstarted/highlight/
│   ├── ProcessHighlightApplication.java
│   ├── constant/
│   │   └── HighlightConstants.java
│   ├── controller/
│   │   └── ProcessHighlightController.java
│   ├── entity/
│   │   └── CamundaHighlightRecord.java
│   ├── listener/
│   │   └── GlobalHighlightRecordListener.java
│   ├── repository/
│   │   └── CamundaHighlightRecordRepository.java
│   └── service/
│       └── ProcessHighlightService.java
└── src/main/resources/
    ├── application.yaml
    ├── bpmn/
    │   └── Process_0op232d.bpmn
    ├── db/
    │   └── highlight.sql
    └── static/
        └── viewer.html
```

## 4. 数据库设计

核心表为 `camunda_highlight_record`：

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `process_instance_id` | 流程实例 ID |
| `process_definition_id` | 流程定义 ID，包含版本信息 |
| `execution_id` | 执行实例 ID |
| `element_id` | 节点 ID 或连线 ID |
| `element_name` | BPMN 元素名称 |
| `element_type` | `TASK` / `GATEWAY` / `START_EVENT` / `END_EVENT` / `FLOW` / `SUBPROCESS` / `OTHER` |
| `event_type` | 当前主要使用 `start` 和 `take` |
| `start_time` | 元素开始时间 |
| `end_time` | 元素结束时间 |
| `assignee` | 任务处理人 |
| `create_time` | 记录创建时间 |


## 5. 快速启动

### 5.1 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x

创建数据库并初始化高亮记录表：

```bash
mysql -u root -p -e "CREATE DATABASE camunda DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p camunda < src/main/resources/db/highlight.sql
```

当前 `application.yaml` 使用如下连接信息：

```yaml
spring.datasource:
  url: jdbc:mysql://localhost:3306/camunda?...
  username: root
  password: root
```

请按本地环境修改用户名和密码。

### 5.2 启动应用

```bash
mvn spring-boot:run
```

或打包运行：

```bash
mvn clean package
java -jar target/camunda-process-highlight-0.0.1-SNAPSHOT.jar
```

应用启动后会自动部署 `Process_0op232d.bpmn`，并启动一个变量 `amount=100` 的流程实例。

### 5.3 查看高亮页面

浏览器访问：

```text
http://localhost:8080/viewer.html
```

页面会自动加载最新流程实例并完成高亮。也可以访问 Camunda Webapp：

```text
http://localhost:8080/camunda/
```

默认管理员账号：

```text
用户名：demo
密码：demo
```

---

## 6. REST API

### 6.1 获取最新流程实例

```http
GET /api/process/default-instance
```

响应示例：

```json
{
  "instanceId": "9f5a1e13-1011-11f1-9f2e-0242ac110002",
  "processDefinitionId": "Process_0op232d:1:8f2b0a12-1011-11f1-9f2e-0242ac110002"
}
```

### 6.2 获取流程高亮数据

```http
GET /api/process/{processInstanceId}/highlight
```

响应示例：

```json
{
  "executedNodeIds": [
    "StartEvent_1",
    "Activity_1dz4vum",
    "Gateway_1oewec6",
    "Activity_0ho5ryh"
  ],
  "executedSequenceFlowIds": [
    "Flow_1gvmgj5",
    "Flow_0he8lna",
    "Flow_0ekgpsu"
  ],
  "currentActivityIds": [
    "Activity_0ho5ryh"
  ],
  "records": []
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `executedNodeIds` | 已经执行过的非连线元素 |
| `executedSequenceFlowIds` | 已经走过的 Sequence Flow |
| `currentActivityIds` | 当前激活节点；流程已结束时为空 |
| `records` | 原始执行记录明细 |

### 6.3 获取 BPMN XML

```http
GET /api/process/bpmn-xml?processDefinitionId={processDefinitionId}
```

该接口从 Camunda Repository 中读取流程模型，供 bpmn-js 渲染使用。

---

## 7. 关键实现点与常见坑

### 7.1 连线事件必须取 `currentTransitionId`

连线 `take` 事件中，当前活动 ID 不是连线 ID。正确写法是：

```java
String flowId = execution.getCurrentTransitionId();
```

如果把连线当成普通活动处理，会导致 Sequence Flow 无法匹配或无法高亮。

### 7.2 节点事件取 `currentActivityId`

节点 `start` / `end` 事件中的元素 ID 来自：

```java
execution.getCurrentActivityId()
```

流程实例级别的 `start` 事件可能拿不到活动 ID，因此监听器需要判空。

### 7.3 任务处理人应通过 Task 事件补充

User Task 的执行事件只能知道“活动开始了”，处理人通常在任务创建或分配后才有。因此需要监听：

- `TaskListener.create`
- `TaskListener.assignment`

并通过 `delegateTask.getTaskDefinitionKey()` 对应到 BPMN 活动 ID。

### 7.4 高亮依赖元素 ID 的一致性

数据库、REST 响应和 bpmn-js 标记都必须使用 BPMN 中的元素 ID，例如：

- 节点：`Activity_1dz4vum`
- 连线：`Flow_1gvmgj5`

因此流程建模时应避免随意修改已上线模型中的元素 ID，否则历史记录会与新模型无法对应。

### 7.5 bpmn-js Viewer 的全局变量

本页面使用 Navigated Viewer，浏览器全局对象仍然是：

```javascript
const viewer = new BpmnJS({ container: '#container' });
```

这里的 `BpmnJS` 实际来自 `bpmn-navigated-viewer.development.js`。

### 7.6 外部 CDN 依赖

`viewer.html` 从 unpkg 加载 bpmn-js 样式和脚本：

```html
https://unpkg.com/bpmn-js@17.11.1/...
```

内网或离线环境需要将资源下载到本地，并从项目静态目录引用。

---

## 8. 当前实现的限制

1. **不支持同一节点/连线的循环计数**  
   当前幂等逻辑是“一个实例 + 一个元素 + 一个事件类型只记一次”。如果流程可能多次进入同一节点，只会保留第一次记录。循环场景应取消全局去重，或按执行次数、执行 ID 保存多条记录。

2. **数据库没有唯一约束**  
   幂等判断在应用层完成，表中暂未定义 `(process_instance_id, element_id, event_type)` 唯一索引。并发或重试下可能产生重复数据，生产环境建议增加唯一约束。

3. **不是完整操作日志**  
   节点结束通过更新 `endTime` 实现，没有单独写入 `EVENT_END` 明细；也没有记录事件原因、跳转类型、取消原因、变量快照等审计信息。

4. **自动启动流程只是演示代码**  
   `PostDeployEvent` 会在应用启动后创建新流程实例，正式项目应通过业务接口、消息或定时任务启动。

5. **数据库配置不适合直接上生产**  
   当前使用 `root/root`、`ddl-auto: update` 和自动建表。生产环境建议改为最小权限账号、Flyway/Liquibase 管理表结构，并关闭 SQL 全量输出。

6. **元素类型分类较粗**  
   `CallActivity`、多实例活动、事务子流程等会被归入较宽泛的类型。若前端需要按类型展示不同样式，需要进一步细分。

---

## 9. 生产化改造建议

- 为自定义记录表增加业务键、租户 ID、流程定义 Key、应用名称等查询字段；
- 增加 `(process_instance_id, element_id, event_type)` 唯一约束或按执行次数生成记录；
- 将高亮记录写入改为异步事件/消息队列，避免监听器同步写库影响流程执行；
- 对高频流程增加批量写入、分库分表或冷热数据归档；
- 将 bpmn-js 资源本地化，并补充加载失败提示；
- 使用 Camunda History API 或自定义审计表补充完整事件流；
- 给 REST 接口增加参数校验、统一异常格式、鉴权和审计日志；
- 使用 Flyway 管理 `camunda_highlight_record` 表结构；
- 为“当前节点”补充等待原因，例如用户任务待办、异步任务执行中、外部任务等待等。

---

## 10. 验证流程是否工作

1. 启动应用；
2. 打开 `http://localhost:8080/viewer.html`；
3. 页面应显示最新实例的 BPMN 图；
4. “起草”及之前的连线应显示绿色；
5. 当前待办节点应显示蓝色；
6. 在 Camunda Tasklist 中完成当前任务；
7. 刷新 `viewer.html`，绿色轨迹应延伸到下一个节点，蓝色节点同步变化；
8. 完成任务直到流程结束，蓝色节点消失，完整路径保持绿色。

---

## 11. 其他

本工程基于 Camunda 官方 Spring Boot 入门示例扩展。
