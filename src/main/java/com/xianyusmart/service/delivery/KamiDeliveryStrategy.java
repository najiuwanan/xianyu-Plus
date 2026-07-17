package com.xianyusmart.service.delivery;

import com.xianyusmart.entity.XianyuKamiItem;
import com.xianyusmart.entity.XianyuGoodsAutoDeliveryConfig;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.service.KamiConfigService;
import com.xianyusmart.service.ApiKamiDeliveryService;
import com.xianyusmart.entity.XianyuKamiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 卡密发货策略（deliveryMode=2）
 *
 * <p>从卡密仓库获取可用卡密，用模板替换 {kmKey} 占位符后返回发货内容。</p>
 *
 * <h3>流程：</h3>
 * <ol>
 *   <li>遍历绑定的卡密配置ID列表（逗号分隔）</li>
 *   <li>按订单购买数量一次性预占完整卡密</li>
 *   <li>发送结果确认后统一提交或释放预占</li>
 *   <li>用模板替换 {kmKey} 占位符</li>
 * </ol>
 */
@Slf4j
@Component
public class KamiDeliveryStrategy implements DeliveryContentStrategy {

    @Autowired
    private KamiConfigService kamiConfigService;

    @Autowired
    private ApiKamiDeliveryService apiKamiDeliveryService;

    @Override
    public boolean supports(int deliveryMode) {
        return deliveryMode == 2;
    }

    @Override
    public String resolve(DeliveryContext context) {
        String content = acquireKamiContent(
                context.getDeliveryConfig().getKamiConfigIds(),
                context.getDeliveryConfig().getKamiDeliveryTemplate(),
                context.getOrderId(),
                context.getAccountId(),
                context.getXyGoodsId(),
                context.getSId(),
                context.getBuyerUserName(),
                context.getQuantity() != null ? context.getQuantity() : 1,
                context.getDeliveryConfig()
        );
        if (content == null) {
            log.warn("【账号{}】卡密发货模式下无可用卡密: xyGoodsId={}, kamiConfigIds={}",
                    context.getAccountId(), context.getXyGoodsId(), context.getDeliveryConfig().getKamiConfigIds());
            return null;
        }
        log.info("【账号{}】卡密发货模式: content长度={}", context.getAccountId(), content.length());
        return content;
    }

    private String acquireKamiContent(String kamiConfigIds, String kamiDeliveryTemplate,
                                       String orderId, Long accountId, String xyGoodsId, String sId,
                                       String buyerUserName, int quantity, XianyuGoodsAutoDeliveryConfig deliveryConfig) {
        if (kamiConfigIds == null || kamiConfigIds.trim().isEmpty()) {
            log.warn("【账号{}】卡密发货未绑定卡密配置: xyGoodsId={}", accountId, xyGoodsId);
            return null;
        }

        String[] configIdArr = kamiConfigIds.split(",");
        for (String configIdStr : configIdArr) {
            boolean apiSource = false;
            try {
                Long configId = Long.parseLong(configIdStr.trim());
                XianyuKamiConfig config = kamiConfigService.getConfig(configId);
                if (config == null) {
                    log.warn("【账号{}】卡券库不存在: configId={}", accountId, configId);
                    continue;
                }
                apiSource = Integer.valueOf(2).equals(config.getSourceType());
                if (apiSource) {
                    String apiContent = apiKamiDeliveryService.acquire(config, DeliveryContext.builder()
                            .accountId(accountId)
                            .xyGoodsId(xyGoodsId)
                            .sId(sId)
                            .orderId(orderId)
                            .buyerUserName(buyerUserName)
                            .quantity(quantity)
                            .deliveryConfig(deliveryConfig)
                            .build());
                    return applyTemplate(kamiDeliveryTemplate, apiContent);
                }
                return kamiConfigService.reserveKami(configId, orderId, quantity).stream()
                        .map(XianyuKamiItem::getKamiContent)
                        .map(kamiContent -> applyTemplate(kamiDeliveryTemplate, kamiContent))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse(null);
            } catch (NumberFormatException e) {
                log.warn("【账号{}】卡密配置ID格式错误: {}", accountId, configIdStr);
            } catch (BusinessException e) {
                if (apiSource) {
                    throw e;
                }
                log.warn("【账号{}】卡密配置无法满足订单: configId={}, orderId={}, reason={}",
                        accountId, configIdStr, orderId, e.getMessage());
            }
        }
        return null;
    }

    private String applyTemplate(String template, String kamiContent) {
        if (template == null || template.isBlank()) {
            return kamiContent;
        }
        return template.replace("{kmKey}", kamiContent);
    }
}
