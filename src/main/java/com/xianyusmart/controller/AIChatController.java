package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.config.rag.DynamicAIChatClientManager;
import com.xianyusmart.controller.dto.ChatWithAIReqDTO;
import com.xianyusmart.service.AIService;
import com.xianyusmart.service.GoodsInfoService;
import com.xianyusmart.service.ItemDetailSyncService;
import com.xianyusmart.controller.dto.SyncSingleItemRespDTO;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.service.reply.ProductAiContextBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI对话控制器
 * 始终加载，AI功能未配置时自动降级
 *
 * @date 2026/4/12 00:16
 */
@RestController
@RequestMapping("/ai")
public class AIChatController {
    @Autowired
    private AIService aiService;

    @Autowired
    private DynamicAIChatClientManager dynamicAIChatClientManager;
    
    @Autowired
    private GoodsInfoService goodsInfoService;

    @Autowired
    private ItemDetailSyncService itemDetailSyncService;
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private ProductAiContextBuilder productAiContextBuilder;

    /**
     * AI对话（流式返回）
     * 未配置API Key时返回降级提示
     */
    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithAi(@RequestBody ChatWithAIReqDTO chatWithAIReqDTO) {
        return aiService.streamChat(chatWithAIReqDTO.getMsg(), null, null);
    }

    /**
     * AI对话测试接口（流式）- 与自动回复流程一致
     * 携带固定资料和商品详情，用于测试提示词与资料效果
     */
    @PostMapping(path = "/chatTest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatTestWithAi(@RequestBody ChatTestReqDTO req) {
        String fixedMaterial = null;
        String goodsDetail = null;
        
        if (req.getAccountId() != null && req.getGoodsId() != null) {
            XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(req.getAccountId(), req.getGoodsId());
            if (config != null) {
                fixedMaterial = productAiContextBuilder.build(config);
            }
            
            String detailInfo = goodsInfoService.getDetailInfoByGoodsId(req.getGoodsId());
            if (detailInfo != null && !detailInfo.isEmpty()) {
                goodsDetail = detailInfo;
            }
        }
        
        return aiService.streamChat(req.getMsg(), fixedMaterial, goodsDetail);
    }

    /**
     * AI状态检测接口
     * 返回AI服务是否可用、配置状态等信息
     */
    @PostMapping("/status")
    public ResultObject<AIStatusRespDTO> getAIStatus() {
        DynamicAIChatClientManager.AIStatusInfo statusInfo = dynamicAIChatClientManager.getStatusInfo();

        AIStatusRespDTO respDTO = new AIStatusRespDTO();
        respDTO.setEnabled(statusInfo.isEnabled());
        respDTO.setAvailable(statusInfo.isAvailable());
        respDTO.setApiKeyConfigured(statusInfo.isApiKeyConfigured());
        respDTO.setMessage(statusInfo.getMessage());
        respDTO.setBaseUrl(statusInfo.getBaseUrl());
        respDTO.setModel(statusInfo.getModel());

        return ResultObject.success(respDTO);
    }

    @PostMapping("/saveFixedMaterial")
    public ResultObject<?> saveFixedMaterial(@RequestBody FixedMaterialReqDTO req) {
        String validationError = validateBargainConfig(req);
        if (validationError != null) return ResultObject.failed(validationError);
        goodsConfigMapper.updateProductAiConfig(
                req.getAccountId(), req.getGoodsId(), req.getFixedMaterial(), req.getAiPrompt(),
                enabled(req.getAiBargainOn()), req.getAiBargainFloorPrice(), req.getAiBargainStepAmount(),
                normalizeRounds(req.getAiBargainMaxRounds()), normalizeStyle(req.getAiBargainStyle()),
                trimToNull(req.getAiBargainFloorReply()), trimToNull(req.getAiBargainInstructions()));
        return ResultObject.success(null);
    }

    @PostMapping("/getFixedMaterial")
    public ResultObject<FixedMaterialRespDTO> getFixedMaterial(@RequestBody FixedMaterialReqDTO req) {
        XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(req.getAccountId(), req.getGoodsId());
        FixedMaterialRespDTO resp = new FixedMaterialRespDTO();
        if (config != null) {
            resp.setFixedMaterial(config.getFixedMaterial());
            resp.setAiPrompt(config.getAiPrompt());
            resp.setAiBargainOn(config.getAiBargainOn());
            resp.setAiBargainFloorPrice(config.getAiBargainFloorPrice());
            resp.setAiBargainStepAmount(config.getAiBargainStepAmount());
            resp.setAiBargainMaxRounds(config.getAiBargainMaxRounds());
            resp.setAiBargainStyle(config.getAiBargainStyle());
            resp.setAiBargainFloorReply(config.getAiBargainFloorReply());
            resp.setAiBargainInstructions(config.getAiBargainInstructions());
        }
        return ResultObject.success(resp);
    }

    @PostMapping("/syncDetailToFixedMaterial")
    public ResultObject<FixedMaterialRespDTO> syncDetailToFixedMaterial(@RequestBody FixedMaterialReqDTO req) {
        String detailInfo = goodsInfoService.getDetailInfoByGoodsId(req.getGoodsId());
        if (detailInfo == null || detailInfo.isBlank()) {
            // 旧逻辑只会复制本地缓存，刚同步或刚上架的商品没有缓存时必然失败。
            // 这里按用户操作实际拉取一次详情，再写入固定资料。
            SyncSingleItemRespDTO syncResult = itemDetailSyncService.syncSingleItem(req.getAccountId(), req.getGoodsId());
            if (!syncResult.isSuccess()) {
                if (syncResult.isVerificationRequired()
                        && syncResult.getCaptchaUrl() != null
                        && !syncResult.getCaptchaUrl().isBlank()) {
                    FixedMaterialRespDTO verificationResp = new FixedMaterialRespDTO();
                    verificationResp.setVerificationRequired(true);
                    verificationResp.setCaptchaUrl(syncResult.getCaptchaUrl());
                    return ResultObject.success(verificationResp);
                }
                return ResultObject.failed(syncResult.getMessage());
            }
            detailInfo = goodsInfoService.getDetailInfoByGoodsId(req.getGoodsId());
        }

        if (detailInfo == null || detailInfo.isBlank()) {
            return ResultObject.failed("闲鱼未返回可用于 AI 回复的商品详情；新上架商品请稍后重试，或手动填写固定资料");
        }

        goodsConfigMapper.updateFixedMaterial(req.getAccountId(), req.getGoodsId(), detailInfo);
        FixedMaterialRespDTO resp = new FixedMaterialRespDTO();
        resp.setFixedMaterial(detailInfo);
        return ResultObject.success(resp);
    }

    public static class FixedMaterialReqDTO {
        private Long accountId;
        private String goodsId;
        private String fixedMaterial;
        private String aiPrompt;
        private Integer aiBargainOn;
        private java.math.BigDecimal aiBargainFloorPrice;
        private java.math.BigDecimal aiBargainStepAmount;
        private Integer aiBargainMaxRounds;
        private String aiBargainStyle;
        private String aiBargainFloorReply;
        private String aiBargainInstructions;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getGoodsId() { return goodsId; }
        public void setGoodsId(String goodsId) { this.goodsId = goodsId; }
        public String getFixedMaterial() { return fixedMaterial; }
        public void setFixedMaterial(String fixedMaterial) { this.fixedMaterial = fixedMaterial; }
        public String getAiPrompt() { return aiPrompt; }
        public void setAiPrompt(String aiPrompt) { this.aiPrompt = aiPrompt; }
        public Integer getAiBargainOn() { return aiBargainOn; }
        public void setAiBargainOn(Integer aiBargainOn) { this.aiBargainOn = aiBargainOn; }
        public java.math.BigDecimal getAiBargainFloorPrice() { return aiBargainFloorPrice; }
        public void setAiBargainFloorPrice(java.math.BigDecimal value) { this.aiBargainFloorPrice = value; }
        public java.math.BigDecimal getAiBargainStepAmount() { return aiBargainStepAmount; }
        public void setAiBargainStepAmount(java.math.BigDecimal value) { this.aiBargainStepAmount = value; }
        public Integer getAiBargainMaxRounds() { return aiBargainMaxRounds; }
        public void setAiBargainMaxRounds(Integer value) { this.aiBargainMaxRounds = value; }
        public String getAiBargainStyle() { return aiBargainStyle; }
        public void setAiBargainStyle(String value) { this.aiBargainStyle = value; }
        public String getAiBargainFloorReply() { return aiBargainFloorReply; }
        public void setAiBargainFloorReply(String value) { this.aiBargainFloorReply = value; }
        public String getAiBargainInstructions() { return aiBargainInstructions; }
        public void setAiBargainInstructions(String value) { this.aiBargainInstructions = value; }
    }

    public static class FixedMaterialRespDTO {
        private String fixedMaterial;
        private String aiPrompt;
        private Integer aiBargainOn;
        private java.math.BigDecimal aiBargainFloorPrice;
        private java.math.BigDecimal aiBargainStepAmount;
        private Integer aiBargainMaxRounds;
        private String aiBargainStyle;
        private String aiBargainFloorReply;
        private String aiBargainInstructions;
        private boolean verificationRequired;
        private String captchaUrl;

        public String getFixedMaterial() { return fixedMaterial; }
        public void setFixedMaterial(String fixedMaterial) { this.fixedMaterial = fixedMaterial; }
        public String getAiPrompt() { return aiPrompt; }
        public void setAiPrompt(String aiPrompt) { this.aiPrompt = aiPrompt; }
        public Integer getAiBargainOn() { return aiBargainOn; }
        public void setAiBargainOn(Integer value) { this.aiBargainOn = value; }
        public java.math.BigDecimal getAiBargainFloorPrice() { return aiBargainFloorPrice; }
        public void setAiBargainFloorPrice(java.math.BigDecimal value) { this.aiBargainFloorPrice = value; }
        public java.math.BigDecimal getAiBargainStepAmount() { return aiBargainStepAmount; }
        public void setAiBargainStepAmount(java.math.BigDecimal value) { this.aiBargainStepAmount = value; }
        public Integer getAiBargainMaxRounds() { return aiBargainMaxRounds; }
        public void setAiBargainMaxRounds(Integer value) { this.aiBargainMaxRounds = value; }
        public String getAiBargainStyle() { return aiBargainStyle; }
        public void setAiBargainStyle(String value) { this.aiBargainStyle = value; }
        public String getAiBargainFloorReply() { return aiBargainFloorReply; }
        public void setAiBargainFloorReply(String value) { this.aiBargainFloorReply = value; }
        public String getAiBargainInstructions() { return aiBargainInstructions; }
        public void setAiBargainInstructions(String value) { this.aiBargainInstructions = value; }
        public boolean isVerificationRequired() { return verificationRequired; }
        public void setVerificationRequired(boolean verificationRequired) { this.verificationRequired = verificationRequired; }
        public String getCaptchaUrl() { return captchaUrl; }
        public void setCaptchaUrl(String captchaUrl) { this.captchaUrl = captchaUrl; }
    }

    public static class ChatTestReqDTO {
        private Long accountId;
        private String goodsId;
        private String msg;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getGoodsId() { return goodsId; }
        public void setGoodsId(String goodsId) { this.goodsId = goodsId; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
    }

    /**
     * AI状态响应DTO
     */
    public static class AIStatusRespDTO {
        private boolean enabled;
        private boolean available;
        private boolean apiKeyConfigured;
        private String message;
        private String baseUrl;
        private String model;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public boolean isApiKeyConfigured() { return apiKeyConfigured; }
        public void setApiKeyConfigured(boolean apiKeyConfigured) { this.apiKeyConfigured = apiKeyConfigured; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    private String validateBargainConfig(FixedMaterialReqDTO req) {
        if (req == null || req.getAccountId() == null || req.getGoodsId() == null || req.getGoodsId().isBlank()) {
            return "账号和商品不能为空";
        }
        if (enabled(req.getAiBargainOn()) == 0) return null;
        if (req.getAiBargainFloorPrice() == null || req.getAiBargainFloorPrice().signum() <= 0) {
            return "开启 AI 议价后必须填写大于 0 的最低成交价";
        }
        if (req.getAiBargainStepAmount() == null || req.getAiBargainStepAmount().signum() <= 0) {
            return "每轮让价金额必须大于 0";
        }
        int rounds = normalizeRounds(req.getAiBargainMaxRounds());
        if (req.getAiBargainMaxRounds() != null && (rounds != req.getAiBargainMaxRounds())) {
            return "最大议价轮数必须在 1 到 10 之间";
        }
        XianyuGoodsInfo goods = goodsInfoMapper.selectOne(new LambdaQueryWrapper<XianyuGoodsInfo>()
                .eq(XianyuGoodsInfo::getXianyuAccountId, req.getAccountId())
                .eq(XianyuGoodsInfo::getXyGoodId, req.getGoodsId())
                .last("LIMIT 1"));
        java.math.BigDecimal listPrice = parsePrice(goods == null ? null : goods.getSoldPrice());
        if (listPrice == null) return "当前商品没有可用标价，请先同步商品后再开启 AI 议价";
        if (req.getAiBargainFloorPrice().compareTo(listPrice) > 0) return "最低成交价不能高于商品当前标价";
        return null;
    }

    private int enabled(Integer value) { return Integer.valueOf(1).equals(value) ? 1 : 0; }
    private int normalizeRounds(Integer value) { return value == null ? 3 : Math.max(1, Math.min(10, value)); }
    private String normalizeStyle(String value) {
        String style = value == null ? "" : value.trim().toUpperCase();
        return switch (style) { case "FIRM", "CLOSE" -> style; default -> "BALANCED"; };
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private java.math.BigDecimal parsePrice(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) return null;
        try { return new java.math.BigDecimal(normalized); } catch (NumberFormatException error) { return null; }
    }
}
