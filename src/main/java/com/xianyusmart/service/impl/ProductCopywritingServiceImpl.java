package com.xianyusmart.service.impl;

import com.xianyusmart.config.rag.DynamicAIChatClientManager;
import com.xianyusmart.controller.dto.ProductCopywritingReqDTO;
import com.xianyusmart.controller.dto.ProductCopywritingRespDTO;
import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.service.ProductCopywritingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class ProductCopywritingServiceImpl implements ProductCopywritingService {
    private static final Set<String> MODES = Set.of("GENERATE", "POLISH", "VARIATION");
    private static final Set<String> STYLES = Set.of("NATURAL", "CONCISE", "DETAILED", "PROMOTIONAL");
    private static final Set<String> TRUSTED_IMAGE_SUFFIXES = Set.of("alicdn.com", "tbcdn.cn", "goofish.com");
    private final DynamicAIChatClientManager aiManager;

    public ProductCopywritingServiceImpl(DynamicAIChatClientManager aiManager) {
        this.aiManager = aiManager;
    }

    @Override
    public ProductCopywritingRespDTO generate(ProductCopywritingReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) throw new BusinessException(400, "请先填写商品标题");
        String mode = normalize(request.getMode(), "GENERATE");
        String style = normalize(request.getStyle(), "NATURAL");
        if (!MODES.contains(mode)) throw new BusinessException(400, "AI 文案模式不正确");
        if (!STYLES.contains(style)) throw new BusinessException(400, "AI 文案风格不正确");
        if (request.getTitle().trim().length() > 60 || text(request.getDescription()).length() > 5000 || text(request.getFacts()).length() > 2000) {
            throw new BusinessException(400, "AI 文案输入内容过长");
        }
        ChatClient client = aiManager.getChatClient();
        if (client == null) throw new BusinessException(409, "AI 服务未启用，请先在系统设置中配置 AI");

        String prompt = buildPrompt(request, mode, style);
        List<Media> media = trustedMedia(request.getImages());
        String result = null;
        boolean imageUsed = false;
        if (!media.isEmpty()) {
            try {
                result = client.prompt()
                        .system(systemPrompt())
                        .user(user -> user.text(prompt).media(media.toArray(Media[]::new)))
                        .call().content();
                imageUsed = true;
            } catch (Exception error) {
                log.warn("[Product copywriting] 当前模型图片理解失败，降级为纯文本生成: {}", error.getMessage());
            }
        }
        if (!StringUtils.hasText(result)) {
            try {
                result = client.prompt().system(systemPrompt()).user(prompt + "\n未使用图片识别，请只依据已提供的文字事实生成。")
                        .call().content();
            } catch (Exception error) {
                log.error("[Product copywriting] AI 文案生成失败", error);
                throw new BusinessException(502, "AI 文案生成失败，请检查模型配置后重试");
            }
        }
        String cleaned = clean(result);
        if (cleaned.isBlank()) throw new BusinessException(502, "AI 没有返回有效商品描述");
        return new ProductCopywritingRespDTO(cleaned.substring(0, Math.min(cleaned.length(), 5000)), imageUsed);
    }

    private String systemPrompt() {
        return "你是闲鱼商品文案助手。只输出可直接填入商品描述框的中文正文，不输出标题、分析、Markdown代码块或免责声明。"
                + "不得编造品牌、型号、成色、功能、配件、库存、价格、发票、售后或发货承诺；信息不足时用自然措辞避开，不要自行补全。"
                + "不得写虚假夸张、绝对化承诺、站外联系方式或规避平台审核的内容。";
    }

    private String buildPrompt(ProductCopywritingReqDTO request, String mode, String style) {
        String action = switch (mode) {
            case "POLISH" -> "润色现有描述，保留全部事实并提升可读性";
            case "VARIATION" -> "生成一个事实完全一致但开头、句式和卖点顺序不同的版本";
            default -> "根据已知信息起草一份商品描述";
        };
        String styleText = switch (style) {
            case "CONCISE" -> "简洁直接，约80到150字";
            case "DETAILED" -> "真实详细，分段清楚，约200到350字";
            case "PROMOTIONAL" -> "有吸引力但不夸张，约150到250字";
            default -> "自然真实，像个人卖家，约120到220字";
        };
        return "任务：" + action + "\n文案风格：" + styleText
                + "\n商品标题：" + request.getTitle().trim()
                + "\n售价：" + (request.getPrice() == null ? "未提供" : request.getPrice().toPlainString() + "元")
                + "\n现有描述：" + blank(request.getDescription())
                + "\n必须遵守的商品事实：" + blank(request.getFacts())
                + "\n差异版本编号：" + (request.getVariationIndex() == null ? 1 : request.getVariationIndex());
    }

    private List<Media> trustedMedia(List<ProductPublishReqDTO.Image> images) {
        List<Media> result = new ArrayList<>();
        for (ProductPublishReqDTO.Image image : images == null ? List.<ProductPublishReqDTO.Image>of() : images) {
            if (result.size() >= 4 || image == null || !StringUtils.hasText(image.getUrl())) continue;
            try {
                URI uri = URI.create(image.getUrl());
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                boolean trusted = "https".equalsIgnoreCase(uri.getScheme()) && TRUSTED_IMAGE_SUFFIXES.stream()
                        .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
                if (trusted) result.add(new Media(imageMime(uri.getPath()), uri));
            } catch (Exception ignored) {
                // 无效图片不会传给模型，文字生成仍可继续。
            }
        }
        return result;
    }

    private MimeType imageMime(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MimeTypeUtils.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MimeTypeUtils.IMAGE_GIF;
        return MimeTypeUtils.IMAGE_JPEG;
    }

    private String clean(String value) {
        String cleaned = text(value).replace("```text", "").replace("```", "").trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("“") && cleaned.endsWith("”"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private String normalize(String value, String fallback) { return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback; }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private String blank(String value) { return StringUtils.hasText(value) ? value.trim() : "未提供"; }
}
