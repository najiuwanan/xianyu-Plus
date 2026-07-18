package com.xianyusmart.service.impl;

import com.xianyusmart.config.rag.DynamicAIChatClientManager;
import com.xianyusmart.service.AIService;
import com.xianyusmart.service.SysSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 服务实现。
 *
 * <p>仅使用对话模型生成回复；商品详情、固定资料和会话上下文会直接组成提示内容。
 * 知识库向量检索已移除，避免额外模型配置和无效 API 调用。</p>
 */
@Service
@Slf4j
public class AIServiceImpl implements AIService {

    private static final String SYS_PROMPT_KEY = "sys_prompt";
    private static final String DEFAULT_SYS_PROMPT = "作为闲鱼虚拟商品店铺客服，请结合商品详情和固定资料，使用简短、自然、准确的中文回答。信息不足时明确说明需要补充的内容，不编造卡密、库存、价格或售后承诺。";
    private static final String AI_NOT_AVAILABLE_MSG = "AI 服务暂未配置，请在系统设置中配置 API Key 后再试。";

    private final DynamicAIChatClientManager dynamicAIChatClientManager;
    private final SysSettingService sysSettingService;

    public AIServiceImpl(DynamicAIChatClientManager dynamicAIChatClientManager,
                         SysSettingService sysSettingService) {
        this.dynamicAIChatClientManager = dynamicAIChatClientManager;
        this.sysSettingService = sysSettingService;
    }

    @Override
    public Flux<String> streamChat(String message, String fixedMaterial, String goodsDetail) {
        ChatClient chatClient = dynamicAIChatClientManager.getChatClient();
        if (chatClient == null) {
            return Flux.just(AI_NOT_AVAILABLE_MSG);
        }

        long startTime = System.currentTimeMillis();
        AtomicBoolean firstTokenLogged = new AtomicBoolean(false);
        return chatClient.prompt()
                .system(buildSystemPrompt(fixedMaterial))
                .user(buildUserPrompt(message, null, goodsDetail))
                .stream()
                .content()
                .doOnNext(token -> {
                    if (firstTokenLogged.compareAndSet(false, true)) {
                        log.info("[AI Chat] 首个响应耗时: {}ms", System.currentTimeMillis() - startTime);
                    }
                })
                .onErrorResume(error -> {
                    log.error("[AI Chat] 流式调用失败", error);
                    return Flux.just("【AI 服务错误】" + extractAiErrorMessage(error));
                });
    }

    @Override
    public String chat(String message, String contextMessages, String fixedMaterial, String goodsDetail) {
        ChatClient chatClient = dynamicAIChatClientManager.getChatClient();
        if (chatClient == null) {
            return AI_NOT_AVAILABLE_MSG;
        }

        try {
            return chatClient.prompt()
                    .system(buildSystemPrompt(fixedMaterial))
                    .user(buildUserPrompt(message, contextMessages, goodsDetail))
                    .call()
                    .content();
        } catch (Exception error) {
            log.error("[AI Chat] 调用失败", error);
            return "AI 回复生成失败：" + extractAiErrorMessage(error);
        }
    }

    @Override
    public String simpleChat(String message) {
        ChatClient chatClient = dynamicAIChatClientManager.getChatClient();
        if (chatClient == null) {
            return null;
        }
        try {
            return chatClient.prompt().user(message).call().content();
        } catch (Exception error) {
            log.error("[AI simpleChat] 调用失败", error);
            return null;
        }
    }

    private String buildSystemPrompt(String fixedMaterial) {
        String configuredPrompt = sysSettingService.getSettingValue(SYS_PROMPT_KEY);
        StringBuilder prompt = new StringBuilder(
                configuredPrompt == null || configuredPrompt.trim().isEmpty()
                        ? DEFAULT_SYS_PROMPT
                        : configuredPrompt.trim()
        );
        if (fixedMaterial != null && !fixedMaterial.trim().isEmpty()) {
            prompt.append("\n\n固定资料：\n").append(fixedMaterial.trim());
        }
        return prompt.toString();
    }

    private String buildUserPrompt(String message, String contextMessages, String goodsDetail) {
        StringBuilder prompt = new StringBuilder();
        if (goodsDetail != null && !goodsDetail.trim().isEmpty()) {
            prompt.append("商品详情：\n").append(goodsDetail.trim()).append("\n\n");
        }
        if (contextMessages != null && !contextMessages.trim().isEmpty()) {
            prompt.append("历史对话：\n").append(formatContextMessages(contextMessages)).append("\n\n");
        }
        prompt.append("买家问题：\n").append(message == null ? "" : message.trim());
        return prompt.toString();
    }

    private String formatContextMessages(String contextMessages) {
        StringBuilder formatted = new StringBuilder();
        for (String line : contextMessages.split("\\n")) {
            if (line.startsWith("user: ")) {
                formatted.append("买家：").append(line.substring(6));
            } else if (line.startsWith("assistant: ")) {
                formatted.append("客服：").append(line.substring(11));
            } else {
                formatted.append(line);
            }
            formatted.append('\n');
        }
        return formatted.toString().trim();
    }

    private String extractAiErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
