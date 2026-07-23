package zxf.flowable.arch.app.controller.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import zxf.flowable.arch.app.exception.BusinessErrorException;
import zxf.flowable.arch.app.service.FlowableService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/form")
public class FormController {
    private final FlowableService flowableService;

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody Map<String, Object> requestBody) throws BusinessErrorException {
        String formId = UUID.randomUUID().toString();
        log.info("start, fromId: {}", formId);
        return flowableService.startProcessWithVariablesInReturn("Flow-Form-Process", formId, Collections.singletonMap("requestBody", requestBody));
    }

    @PostMapping("/message")
    public Map<String, Object> message(@RequestParam String formId, @RequestParam String action, @RequestBody Map<String, Object> messageBody) throws BusinessErrorException {
        log.info("message, fromId: {}, action: {}", formId, action);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("messageAction", action);
        parameters.put("messageBody", messageBody);

        return flowableService.correlateMessageWithVariablesInReturn("FormProcess.message", formId, parameters);
    }
}
