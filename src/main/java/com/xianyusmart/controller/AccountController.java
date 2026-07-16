package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.controller.dto.AccountReqDTO;
import com.xianyusmart.controller.dto.AddAccountRespDTO;
import com.xianyusmart.controller.dto.DeleteAccountReqDTO;
import com.xianyusmart.controller.dto.DeleteAccountRespDTO;
import com.xianyusmart.controller.dto.GetAccountDetailReqDTO;
import com.xianyusmart.controller.dto.GetAccountDetailRespDTO;
import com.xianyusmart.controller.dto.GetAccountListRespDTO;
import com.xianyusmart.controller.dto.ManualAddAccountReqDTO;
import com.xianyusmart.controller.dto.UpdateAccountReqDTO;
import com.xianyusmart.controller.dto.UpdateAccountRespDTO;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.AutoReplyDelayService;
import com.xianyusmart.service.DeliveryTaskService;
import com.xianyusmart.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账号管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired
    private XianyuAccountMapper accountMapper;
    
    @Autowired
    private AccountService accountService;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private DeliveryTaskService deliveryTaskService;

    @Autowired
    private AutoReplyDelayService autoReplyDelayService;

    /**
     * 获取账号列表
     */
    @PostMapping("/list")
    public ResultObject<GetAccountListRespDTO> getAccountList() {
        try {
            List<XianyuAccount> accounts = accountMapper.selectList(null);
            GetAccountListRespDTO respDTO = new GetAccountListRespDTO();
            respDTO.setAccounts(accounts);
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取账号列表失败", e);
            return ResultObject.failed("获取账号列表失败: " + e.getMessage());
        }
    }

    /**
     * 添加账号
     */
    @PostMapping("/add")
    public ResultObject<AddAccountRespDTO> addAccount(@RequestBody AccountReqDTO reqDTO) {
        try {
            log.info("添加账号请求: accountNote={}", reqDTO.getAccountNote());
            
            if (reqDTO.getCookie() == null || reqDTO.getCookie().isEmpty()) {
                return ResultObject.failed("Cookie不能为空");
            }
            
            Long accountId = accountService.saveAccountAndCookie(
                    reqDTO.getAccountNote(),
                    reqDTO.getUnb(),
                    reqDTO.getCookie()
            );
            
            AddAccountRespDTO respDTO = new AddAccountRespDTO();
            respDTO.setAccountId(accountId);
            respDTO.setMessage("添加成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("添加账号失败", e);
            return ResultObject.failed("添加账号失败: " + e.getMessage());
        }
    }

    /**
     * 手动添加账号
     */
    @PostMapping("/manualAdd")
    public ResultObject<AddAccountRespDTO> manualAddAccount(@RequestBody ManualAddAccountReqDTO reqDTO) {
        try {
            log.info("手动添加账号请求: accountNote={}", reqDTO.getAccountNote());
            
            if (reqDTO.getCookie() == null || reqDTO.getCookie().isEmpty()) {
                return ResultObject.failed("Cookie不能为空");
            }
            
            // 从Cookie中提取unb信息
            String unb = extractUnbFromCookie(reqDTO.getCookie());
            if (unb == null || unb.isEmpty()) {
                return ResultObject.failed("无法从Cookie中提取UNB信息");
            }
            
            // 检查账号是否已存在
            Long existingAccountId = accountService.getAccountIdByUnb(unb);
            if (existingAccountId != null) {
                return ResultObject.failed("账号已存在");
            }
            
            // 保存账号和Cookie信息
            Long accountId = accountService.saveAccountAndCookie(
                    reqDTO.getAccountNote(),
                    unb,
                    reqDTO.getCookie()
            );
            
            AddAccountRespDTO respDTO = new AddAccountRespDTO();
            respDTO.setAccountId(accountId);
            respDTO.setMessage("添加成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("手动添加账号失败", e);
            return ResultObject.failed("添加账号失败: " + e.getMessage());
        }
    }
    
    /**
     * 从Cookie字符串中提取UNB值
     *
     * @param cookie Cookie字符串
     * @return UNB值，如果未找到则返回null
     */
    private String extractUnbFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return null;
        }
        
        // 查找unb=后面的值
        String[] cookieParts = cookie.split(";\\s*");
        for (String part : cookieParts) {
            if (part.startsWith("unb=")) {
                return part.substring(4); // "unb=".length() = 4
            }
        }
        
        return null;
    }

    /**
     * 更新账号
     */
    @PostMapping("/update")
    public ResultObject<UpdateAccountRespDTO> updateAccount(@RequestBody UpdateAccountReqDTO reqDTO) {
        try {
            log.info("更新账号请求: accountId={}", reqDTO.getAccountId());
            
            if (reqDTO.getAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            
            XianyuAccount account = accountMapper.selectById(reqDTO.getAccountId());
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            
            // 只更新账号备注
            if (reqDTO.getAccountNote() != null) {
                account.setAccountNote(reqDTO.getAccountNote());
            }
            if (reqDTO.getAutoRateEnabled() != null) {
                account.setAutoRateEnabled(reqDTO.getAutoRateEnabled());
            }
            if (reqDTO.getAutoRateText() != null) {
                account.setAutoRateText(reqDTO.getAutoRateText());
            }
            if (reqDTO.getAutoAskFlower() != null) {
                account.setAutoAskFlower(reqDTO.getAutoAskFlower());
            }
            if (reqDTO.getAutoAskFlowerText() != null) {
                account.setAutoAskFlowerText(reqDTO.getAutoAskFlowerText());
            }
            if (reqDTO.getAutoConnectOnStartup() != null) {
                account.setAutoConnectOnStartup(reqDTO.getAutoConnectOnStartup() == 0 ? 0 : 1);
            }
            
            accountMapper.updateById(account);
            
            // 不再更新Cookie和UNB
            
            UpdateAccountRespDTO respDTO = new UpdateAccountRespDTO();
            respDTO.setMessage("更新成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("更新账号失败", e);
            return ResultObject.failed("更新账号失败: " + e.getMessage());
        }
    }

    /**
     * 临时下线或恢复账号。禁用时立即关闭实时连接并暂停尚未完成的自动发货任务。
     */
    @PostMapping("/setEnabled")
    public ResultObject<String> setAccountEnabled(@RequestBody AccountEnabledReqDTO reqDTO) {
        if (reqDTO.getAccountId() == null || reqDTO.getEnabled() == null) {
            return ResultObject.failed("账号ID和启用状态不能为空");
        }

        try {
            XianyuAccount account = accountMapper.selectById(reqDTO.getAccountId());
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }

            if (reqDTO.getEnabled()) {
                if (!Integer.valueOf(0).equals(account.getStatus())) {
                    return ResultObject.failed("只有已禁用的账号可以通过此操作启用");
                }
                account.setStatus(1);
                accountMapper.updateById(account);

                boolean shouldReconnect = !Integer.valueOf(0).equals(account.getAutoConnectOnStartup());
                boolean connected = shouldReconnect && webSocketService.startWebSocket(account.getId());
                return ResultObject.success(connected
                        ? "账号已启用，实时连接已恢复"
                        : "账号已启用，请在连接管理中确认实时连接状态");
            }

            if (!Integer.valueOf(1).equals(account.getStatus())) {
                return ResultObject.failed("当前账号不是正常状态，无法直接禁用");
            }
            account.setStatus(0);
            accountMapper.updateById(account);
            deliveryTaskService.pauseAccountTasks(account.getId());
            autoReplyDelayService.cancelAccountTasks(account.getId());
            webSocketService.stopWebSocket(account.getId());
            return ResultObject.success("账号已禁用：实时连接与自动化任务已暂停");
        } catch (Exception e) {
            log.error("切换账号启用状态失败: accountId={}", reqDTO.getAccountId(), e);
            return ResultObject.failed("切换账号状态失败: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class AccountEnabledReqDTO {
        private Long accountId;
        private Boolean enabled;
    }

    /**
     * 删除账号
     */
    @PostMapping("/delete")
    public ResultObject<DeleteAccountRespDTO> deleteAccount(@RequestBody DeleteAccountReqDTO reqDTO) {
        try {
            Long id = reqDTO.getAccountId();
            log.info("删除账号请求: accountId={}", id);
            
            XianyuAccount account = accountMapper.selectById(id);
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            
            // 删除账号关联的所有数据
            accountService.deleteAccountAndRelatedData(id);
            
            DeleteAccountRespDTO respDTO = new DeleteAccountRespDTO();
            respDTO.setMessage("删除成功");
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("删除账号失败", e);
            return ResultObject.failed("删除账号失败: " + e.getMessage());
        }
    }

    /**
     * 获取账号详情
     */
    @PostMapping("/detail")
    public ResultObject<GetAccountDetailRespDTO> getAccountDetail(@RequestBody GetAccountDetailReqDTO reqDTO) {
        try {
            Long id = reqDTO.getAccountId();
            XianyuAccount account = accountMapper.selectById(id);
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }
            GetAccountDetailRespDTO respDTO = new GetAccountDetailRespDTO();
            respDTO.setAccount(account);
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取账号详情失败", e);
            return ResultObject.failed("获取账号详情失败: " + e.getMessage());
        }
    }



}
