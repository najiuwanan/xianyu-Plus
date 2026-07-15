# Event 包说明

本包实现了基于Spring事件驱动的异步处理架构，用于解耦业务逻辑。

## 目录结构

```
event/
├── chatMessageEvent/          # 聊天消息相关事件
│   ├── ChatMessageReceivedEvent.java              # 消息接收事件
│   ├── ChatMessageEventSaveListener.java          # 消息保存监听器
│   ├── ChatMessageEventAutoDeliveryListener.java  # 自动发货监听器
│   └── README.md                                  # 详细说明文档
└── README.md                                      # 本文件
```

## 设计原则

### 1. 按业务领域分包
- 每个业务领域（如聊天消息、订单、商品等）独立成包
- 包内包含该领域的事件定义和监听器
- 保持代码结构清晰，易于维护和扩展

### 2. 事件驱动架构
- 使用Spring的ApplicationEvent机制
- 采用发布-订阅模式，实现业务解耦
- 支持一对多广播，多个监听器可同时监听同一事件

### 3. 异步处理
- 所有监听器使用@Async异步执行
- 不阻塞主线程，提高系统响应速度
- 监听器之间互不影响，独立执行

## 现有模块

### chatMessageEvent - 聊天消息事件模块
处理WebSocket接收到的聊天消息，包括：
- 消息异步保存到数据库
- 自动发货判断和执行
- 其他消息相关业务逻辑

详细说明请参考：[chatMessageEvent/README.md](chatMessageEvent/README.md)

## 如何添加新的事件模块

### 1. 创建新的子包
在event包下创建新的子包，例如：`orderEvent`、`goodsEvent`等

### 2. 定义事件类
```java
package com.xianyusmart.event.orderEvent;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderCreatedEvent extends ApplicationEvent {
    private final Order order;
    
    public OrderCreatedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}
```

### 3. 创建监听器
```java
package com.xianyusmart.event.orderEvent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderNotificationListener {
    
    @Async
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        // 处理订单通知逻辑
    }
}
```

### 4. 发布事件
在Service中注入ApplicationEventPublisher并发布事件：
```java
@Autowired
private ApplicationEventPublisher eventPublisher;

public void createOrder(Order order) {
    // 保存订单
    orderMapper.insert(order);
    
    // 发布事件
    OrderCreatedEvent event = new OrderCreatedEvent(this, order);
    eventPublisher.publishEvent(event);
}
```

## 注意事项

1. **包命名规范**：使用小驼峰命名，如`chatMessageEvent`、`orderEvent`
2. **事件命名规范**：使用过去式，如`OrderCreatedEvent`、`MessageReceivedEvent`
3. **监听器命名规范**：`{Event名称}{业务功能}Listener`
4. **异常处理**：监听器内部必须捕获异常，避免影响其他监听器
5. **事务管理**：异步方法中的事务需要单独管理
6. **性能监控**：建议监控线程池使用情况，及时调整参数

## 配置说明

异步线程池配置参见：`AsyncConfig` 类

- **核心线程数**：5
- **最大线程数**：10
- **队列容量**：100
- **线程名称前缀**：`async-event-`

可根据实际情况调整参数。
