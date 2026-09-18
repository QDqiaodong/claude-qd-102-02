package com.laundry.room.controller;

import com.laundry.room.entity.Linen;
import com.laundry.room.service.LinenService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LinenController {

    private final LinenService service;

    public LinenController(LinenService service) {
        this.service = service;
    }

    @GetMapping("/linens")
    public List<Linen> list(@RequestParam(required = false) String category,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String keyword) {
        return service.list(category, status, keyword);
    }

    @PostMapping("/linens")
    public Linen create(@RequestBody Linen input) {
        return service.create(input);
    }

    @PutMapping("/linens/{id}")
    public Linen update(@PathVariable Long id, @RequestBody Linen input) {
        return service.update(id, input);
    }
}
