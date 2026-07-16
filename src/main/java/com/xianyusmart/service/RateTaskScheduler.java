package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 自动评价定时任务
 */
@Slf4j
@Component
public class RateTaskScheduler {

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private RateService rateService;

    /**
     * 默认每两分钟扫描一次。买家确认收货后，闲鱼把订单放入待评价列表即可处理；
     * 间隔可通过 app.automation.rate-scan-delay-ms 配置调整。
     */
    @Scheduled(fixedDelayString = "${app.automation.rate-scan-delay-ms:120000}",
            initialDelayString = "${app.automation.rate-scan-initial-delay-ms:30000}")
    public void scheduleAutoRate() {
        log.info("【自动评价任务】开始执行定时扫描...");
        
        // 查找所有状态正常且开启了自动评价的账号
        List<XianyuAccount> accounts = accountMapper.selectList(new QueryWrapper<XianyuAccount>()
                .eq("status", 1)
                .eq("auto_rate_enabled", 1));

        if (accounts.isEmpty()) {
            log.info("【自动评价任务】没有开启自动评价的账号");
            return;
        }

        for (XianyuAccount account : accounts) {
            processAutoRateForAccount(account);
        }
        log.info("【自动评价任务】执行完毕。");
    }

    private void processAutoRateForAccount(XianyuAccount account) {
        try {
            Long accountId = account.getId();
            log.info("【自动评价任务】正在扫描账号 {}", accountId);

            List<Map<String, Object>> pendingList = rateService.getPendingRateList(accountId);
            if (pendingList == null || pendingList.isEmpty()) {
                log.info("【自动评价任务】账号 {} 没有待评价订单", accountId);
                return;
            }

            String feedbackText = StringUtils.hasText(account.getAutoRateText()) 
                    ? account.getAutoRateText() 
                    : "不错的买家！";

            for (Map<String, Object> item : pendingList) {
                String tradeId = rateService.extractTradeId(item);
                if (tradeId != null) {
                    // 执行评价
                    rateService.rateBuyer(accountId, tradeId, feedbackText);
                    // 每次评价间隔 2 秒，防止过快被限制
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            log.error("【自动评价任务】账号 {} 处理失败: {}", account.getId(), e.getMessage());
        }
    }

}
