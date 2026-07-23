package zxf.flowable.arch.rest.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

public class TaskService {
    public static ResponseEntity<Map<String, Object>> result(Integer task, String service) throws InterruptedException {
        Map<String, Object> result = new HashMap<>();
        result.put("code", task.toString());
        result.put("task", service + "-" + task);
        result.put("value", LocalDate.now());
        switch (HttpStatus.resolve(task)) {
            case OK:
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
