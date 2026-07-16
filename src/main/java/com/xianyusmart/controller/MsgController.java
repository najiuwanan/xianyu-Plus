package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.MsgContextReqDTO;
import com.xianyusmart.controller.dto.MsgListReqDTO;
import com.xianyusmart.controller.dto.MsgListRespDTO;
import com.xianyusmart.controller.dto.ChatSessionDTO;
import com.xianyusmart.controller.dto.ChatSessionReqDTO;
import com.xianyusmart.controller.dto.ChatTakeoverReqDTO;
import com.xianyusmart.service.ChatMessageService;
import com.xianyusmart.service.reply.HumanTakeoverManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 消息管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/msg")
public class MsgController {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private HumanTakeoverManager humanTakeoverManager;

    /**
     * 分页查询消息列表
     * 按时间排序，时间新的在前面
     *
     * @param reqDTO 请求参数
     * @return 消息列表
     */
    @PostMapping("/list")
    public ResultObject<MsgListRespDTO> getMessageList(@RequestBody MsgListReqDTO reqDTO) {
        try {
            log.info("查询消息列表请求: xianyuAccountId={}, xyGoodsId={}, filterCurrentAccount={}, pageNum={}, pageSize={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getFilterCurrentAccount(), reqDTO.getPageNum(), reqDTO.getPageSize());
            return chatMessageService.getMessageList(reqDTO);
        } catch (Exception e) {
            log.error("查询消息列表失败", e);
            return ResultObject.failed("查询消息列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据会话ID获取上下文消息（最近50条）
     *
     * @param reqDTO 请求参数（sid, limit）
     * @return 消息列表
     */
    @PostMapping("/context")
    public ResultObject<?> getContextMessages(@RequestBody MsgContextReqDTO reqDTO) {
        try {
            log.info("查询上下文消息请求: sid={}, limit={}", reqDTO.getSid(), reqDTO.getLimit());
            return chatMessageService.getContextMessages(reqDTO);
        } catch (Exception e) {
            log.error("查询上下文消息失败", e);
            return ResultObject.failed("查询上下文消息失败: " + e.getMessage());
        }
    }

    /** 在线客服的会话侧边栏数据。 */
    @PostMapping("/sessions")
    public ResultObject<java.util.List<ChatSessionDTO>> getSessions(@RequestBody ChatSessionReqDTO reqDTO) {
        return chatMessageService.getSessionList(reqDTO);
    }

    /** 手动切换会话接管状态；结束接管后将再次允许该商品的自动回复策略处理新消息。 */
    @PostMapping("/takeover")
    public ResultObject<String> updateTakeover(@RequestBody ChatTakeoverReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null || reqDTO.getSid() == null || reqDTO.getSid().isBlank()) {
            return ResultObject.validateFailed("账号和会话不能为空");
        }
        if (Boolean.FALSE.equals(reqDTO.getEnabled())) {
            humanTakeoverManager.release(reqDTO.getXianyuAccountId(), reqDTO.getSid());
            return ResultObject.success("已恢复自动回复");
        }
        int minutes = reqDTO.getDurationMinutes() == null ? 10
                : Math.max(1, Math.min(reqDTO.getDurationMinutes(), 1440));
        humanTakeoverManager.takeover(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getSid(), minutes);
        return ResultObject.success("已人工接管 " + minutes + " 分钟");
    }
}

