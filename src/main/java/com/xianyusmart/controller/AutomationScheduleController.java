package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.AutomationScheduleConfigDTO;
import com.xianyusmart.controller.dto.AutomationScheduleSaveReqDTO;
import com.xianyusmart.service.AutomationScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API for user-configurable business automation scan intervals. */
@RestController
@RequestMapping("/api/automation-schedule")
public class AutomationScheduleController {

    private final AutomationScheduleService automationScheduleService;

    public AutomationScheduleController(AutomationScheduleService automationScheduleService) {
        this.automationScheduleService = automationScheduleService;
    }

    @PostMapping("/list")
    public ResultObject<List<AutomationScheduleConfigDTO>> list() {
        return ResultObject.success(automationScheduleService.list());
    }

    @PostMapping("/save")
    public ResultObject<List<AutomationScheduleConfigDTO>> save(
            @Valid @RequestBody AutomationScheduleSaveReqDTO request) {
        try {
            return ResultObject.success(automationScheduleService.save(request));
        } catch (IllegalArgumentException exception) {
            return ResultObject.validateFailed(exception.getMessage());
        }
    }
}
