package com.xianyusmart.service;

import reactor.core.publisher.Flux;

/**
 * AI 对话服务。
 *
 * <p>自动回复只使用系统提示词、商品详情、固定资料和可选会话上下文，
 * 不再维护独立的知识库检索或向量模型。</p>
 */
public interface AIService {

    /** 流式 AI 对话，用于前端测试。 */
    Flux<String> streamChat(String message, String fixedMaterial, String goodsDetail);

    /** 完整 AI 对话，用于自动回复。 */
    String chat(String message, String contextMessages, String fixedMaterial, String goodsDetail);

    /** 简短的通用 AI 调用，用于关键词回复润色。 */
    String simpleChat(String message);
}
