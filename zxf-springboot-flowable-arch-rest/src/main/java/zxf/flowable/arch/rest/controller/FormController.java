package zxf.flowable.arch.rest.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zxf.flowable.arch.rest.request.FormRequest;
import zxf.flowable.arch.rest.request.TaskRequest;
import zxf.flowable.arch.rest.service.TaskService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestController
@RequestMapping("/form")
public class FormController {
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> create(@RequestBody FormRequest formRequest) throws InterruptedException {
        return result(formRequest);
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> update(@RequestBody FormRequest formRequest) throws InterruptedException {
        return result(formRequest);
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestBody FormRequest formRequest) throws InterruptedException {
        return result(formRequest);
    }

    public static ResponseEntity<Map<String, Object>> result(FormRequest formRequest) throws InterruptedException {
        Map<String, Object> result = new HashMap<>();
        result.put("code", formRequest.getCode().toString());
        result.put("time", LocalDate.now());
        switch (HttpStatus.resolve(formRequest.getCode())) {
            case OK:
                result.put("data", formRequest);
                return ResponseEntity.ok().body(result);
            case BAD_REQUEST:
                return ResponseEntity.status(BAD_REQUEST).body(result);
            case REQUEST_TIMEOUT:
                Thread.sleep(1000 * 600);
                return ResponseEntity.ok().body(result);
            case SERVICE_UNAVAILABLE:
                return ResponseEntity.status(SERVICE_UNAVAILABLE).body(result);
            default:
                return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
