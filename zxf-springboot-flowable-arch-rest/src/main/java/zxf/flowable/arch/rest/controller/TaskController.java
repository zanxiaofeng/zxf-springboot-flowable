package zxf.flowable.arch.rest.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zxf.flowable.arch.rest.request.TaskRequest;
import zxf.flowable.arch.rest.service.TaskService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/task")
public class TaskController {
    @PostMapping("/a")
    public ResponseEntity<Map<String, Object>> taskA(@RequestBody TaskRequest taskRequest) throws InterruptedException {
        return TaskService.result(taskRequest.getTask(), "A");
    }

    @PostMapping("/b")
    public ResponseEntity<Map<String, Object>> taskB(@RequestBody TaskRequest taskRequest) throws InterruptedException {
        return TaskService.result(taskRequest.getTask(), "B");
    }

    @PostMapping("/c")
    public ResponseEntity<Map<String, Object>> taskC(@RequestBody TaskRequest taskRequest) throws InterruptedException {
        return TaskService.result(taskRequest.getTask(), "C");
    }

    @PostMapping("/d")
    public ResponseEntity<Map<String, Object>> taskD(@RequestBody TaskRequest taskRequest) throws InterruptedException {
        return TaskService.result(taskRequest.getTask(), "D");
    }
}
