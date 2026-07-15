# ChatMessageEvent 包说明

本包实现了基于Spring事件驱动的异步消息处理架构。

## 架构设计

采用**一对多广播模式**，一个事件被多个独立监听器同时监听，实现业务解耦。

```
WebSocket消息接收
    ↓
解析消息
    ↓
发布 ChatMessageReceivedEvent（一对多广播）
    ↓
主线程返回（不等待）
    ↓
    ├─→ [异步线程1] ChatMessageEventSaveListener
    │       ↓
    │   保存消息到数据库
    │
    └─→ [异步线程2] ChatMessageEventAutoDeliveryListener
            ↓
        判断并执行自动发货
```

## 核心组件

### 1. 事件（Event）

#### ChatMessageReceivedEvent
- **作用**：WebSocket接收到消息并解析后触发
- **携带数据**：完整的 `XianyuChatMessage` 对象（尚未入库）
- **发布者**：`ChatMessageServiceImpl`
- **监听者**：`ChatMessageEventSaveListener`、`ChatMessageEventAutoDeliveryListener`

### 2. 监听器（Listeners）

#### ChatMessageEventSaveListener
- **监听事件**：`ChatMessageReceivedEvent`
- **职责**：异步保存消息到数据库
- **特点**：
  - 使用 `@Async` 异步执行
  - 自动去重，避免重复保存
  - 独立模块，与其他监听器互不影响

#### ChatMessageEventAutoDeliveryListener
- **监听事件**：`ChatMessageReceivedEvent`
- **职责**：判断并执行自动发货
- **触发条件**：
  - `contentType = 26`（已付款待发货）
  - `msgContent` 包含 `"[已付款，待发货]"`
- **执行流程**：
  1. 从消息内容中提取买家名称
  2. 创建发货记录（state=0，待发货）
  3. 检查商品是否开启自动发货
  4. 获取自动发货配置内容
  5. 模拟人工操作延迟（阅读、思考、打字）
  6. 发送发货消息给买家
  7. 更新发货记录状态（1=成功，-1=失败）

## 执行流程

### 完整流程图

```
WebSocket接收消息
    ↓
SyncMessageHandler 解密消息
    ↓
ChatMessageService 解析消息
    ↓
发布 ChatMessageReceivedEvent
    ↓
主线程返回（不等待）
    ↓
    ├─→ [异步线程1] ChatMessageEventSaveListener
    │       ↓
    │   保存消息到数据库
    │
    └─→ [异步线程2] ChatMessageEventAutoDeliveryListener
            ↓
        判断是否需要自动发货
            ↓ 是
        创建发货记录（state=0）
            ↓
        执行自动发货
            ↓
        更新记录状态（1=成功，-1=失败）
```

### 时序说明

1. **消息接收**：WebSocket接收消息（同步）
2. **消息解析**：解析消息字段（同步）
3. **事件发布**：发布 `ChatMessageReceivedEvent`（同步）
4. **主线程返回**：立即返回，不等待监听器执行
5. **异步处理**：所有监听器在独立线程中并发执行

## 优势

### 1. 高性能
- 消息接收后立即返回，不阻塞WebSocket
- 多个监听器并发执行，充分利用CPU资源
- 使用线程池管理，避免线程创建开销

### 2. 高可扩展性
- 新增业务只需添加新的监听器
- 不需要修改现有代码
- 监听器之间完全解耦

### 3. 高可靠性
- 监听器异常不影响其他监听器
- 消息保存失败不影响自动发货判断
- 自动发货失败会记录状态，便于排查

### 4. 易于维护
- 每个监听器职责单一
- 代码结构清晰
- 详细的注释和日志

## 如何扩展

### 添加新的业务监听器

创建新的监听器类，监听同一个事件：

```java
@Slf4j
@Component
public class ChatMessageEventNewBusinessListener {
    
    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        XianyuChatMessage message = event.getChatMessage();
        
        try {
            // 实现你的业务逻辑
            // 例如：消息统计、通知推送、数据分析等
            
        } catch (Exception e) {
            log.error("处理新业务异常", e);
        }
    }
}
```

监听器会自动注册，无需其他配置。所有监听器并发执行，互不影响。

## 配置说明

### 异步线程池配置

参见 `AsyncConfig` 类：

- **核心线程数**：5
- **最大线程数**：10
- **队列容量**：100
- **线程名称前缀**：`async-event-`

可根据实际情况调整参数。

## 注意事项

1. **异步执行**：所有监听器使用 `@Async` 异步执行
2. **异常处理**：监听器内部需要捕获异常，避免影响其他监听器
3. **事务管理**：异步方法中的事务需要单独管理
4. **顺序保证**：监听器执行顺序不保证，如有顺序要求需要特殊处理
5. **性能监控**：建议监控线程池使用情况，及时调整参数

## 文件清单

- `ChatMessageReceivedEvent.java` - 聊天消息接收事件
- `ChatMessageEventSaveListener.java` - 消息保存监听器
- `ChatMessageEventAutoDeliveryListener.java` - 自动发货监听器
- `README.md` - 本说明文档
