package com.laundry.room.controller;

import com.laundry.room.entity.WashShortage;
import com.laundry.room.service.WashShortageService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WashShortageController {

    private final WashShortageService service;

    public WashShortageController(WashShortageService service) {
        this.service = service;
    }

    @GetMapping("/shortages")
    public List<WashShortage> list(@RequestParam(required = false) Long linenId,
                                   @RequestParam(required = false) String status) {
        return service.list(linenId, status);
    }

    /** 结案路径一：短少件数补回，当场加回可领用在库。 */
    @PostMapping("/shortages/{id}/replenish")
    public WashShortage replenish(@PathVariable Long id) {
        return service.replenish(id);
    }

    /** 结案路径二：转进报损赔付等确认；确认前追差仍未结案，件数不进在库。 */
    @PostMapping("/shortages/{id}/to-loss")
    public WashShortage toLoss(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        return service.toLoss(id, reason);
    }
}
