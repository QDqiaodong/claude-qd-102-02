package com.laundry.room.controller;

import com.laundry.room.dto.BatchDonePayload;
import com.laundry.room.entity.WashBatch;
import com.laundry.room.service.WashBatchService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WashBatchController {

    private final WashBatchService service;

    public WashBatchController(WashBatchService service) {
        this.service = service;
    }

    @GetMapping("/batches")
    public List<WashBatch> list(@RequestParam(required = false) Long linenId,
                                @RequestParam(required = false) String status) {
        return service.list(linenId, status);
    }

    @PostMapping("/batches")
    public WashBatch create(@RequestBody WashBatch input) {
        return service.create(input);
    }

    /** 推进状态机；action=done 时可带收工信息（回洗件数 / 责任 / 发现人）。 */
    @PostMapping("/batches/{id}/advance")
    public WashBatch advance(@PathVariable Long id, @RequestParam String action,
                             @RequestBody(required = false) BatchDonePayload payload) {
        return service.advance(id, action, payload);
    }
}
