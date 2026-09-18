package com.laundry.room.controller;

import com.laundry.room.entity.FloorIssue;
import com.laundry.room.service.FloorIssueService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FloorIssueController {

    private final FloorIssueService service;

    public FloorIssueController(FloorIssueService service) {
        this.service = service;
    }

    @GetMapping("/issues")
    public List<FloorIssue> list(@RequestParam(required = false) String floorCode,
                                 @RequestParam(required = false) String status) {
        return service.list(floorCode, status);
    }

    @PostMapping("/issues")
    public FloorIssue create(@RequestBody FloorIssue input) {
        return service.create(input);
    }

    @PostMapping("/issues/{id}/giveback")
    public FloorIssue giveBack(@PathVariable Long id, @RequestParam Integer backQty) {
        return service.giveBack(id, backQty);
    }
}
