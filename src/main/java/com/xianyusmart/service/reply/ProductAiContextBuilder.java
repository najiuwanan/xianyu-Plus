package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuGoodsConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一组合商品专属 AI 配置，避免商品提示词与固定资料在不同入口表现不一致。
 */
@Component
public class ProductAiContextBuilder {

    public String build(XianyuGoodsConfig config) {
        if (config == null) {
            return null;
        }

        List<String> sections = new ArrayList<>();
        if (hasText(config.getAiPrompt())) {
            sections.add("商品专属回复规则：\n" + config.getAiPrompt().trim());
        }
        if (hasText(config.getFixedMaterial())) {
            sections.add("商品固定资料：\n" + config.getFixedMaterial().trim());
        }
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
