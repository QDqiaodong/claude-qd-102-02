package com.laundry.room.controller;

import com.laundry.room.entity.LinenLoss;
import com.laundry.room.service.LinenLossService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LinenLossController {

    private final LinenLossService service;

    public LinenLossController(LinenLossService service) {
        this.service = service;
    }

    @GetMapping("/losses")
    public List<LinenLoss> list(@RequestParam(required = false) Long linenId,
                                @RequestParam(required = false) String status) {
        return service.list(linenId, status);
    }

    @PostMapping("/losses")
    public LinenLoss create(@RequestBody LinenLoss input) {
        return service.create(input);
    }

    @PostMapping("/losses/{id}/confirm")
    public LinenLoss confirm(@PathVariable Long id) {
        return service.confirm(id);
    }
}
