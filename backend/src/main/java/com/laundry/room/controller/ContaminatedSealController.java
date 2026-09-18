package com.laundry.room.controller;

import com.laundry.room.entity.ContaminatedSeal;
import com.laundry.room.entity.WashBatch;
import com.laundry.room.service.ContaminatedSealService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ContaminatedSealController {

    private final ContaminatedSealService service;

    public ContaminatedSealController(ContaminatedSealService service) {
        this.service = service;
    }

    @GetMapping("/seals")
    public List<ContaminatedSeal> list(@RequestParam(required = false) Long linenId,
                                       @RequestParam(required = false) String status) {
        return service.list(linenId, status);
    }

    @PostMapping("/seals")
    public ContaminatedSeal create(@RequestBody ContaminatedSeal input) {
        return service.create(input);
    }

    /** 挂专洗批次：洗法固定专洗，件数取封存还没解除的件数，不走在库校验。 */
    @PostMapping("/seals/{id}/special-wash")
    public WashBatch attachSpecialWash(@PathVariable Long id, @RequestBody WashBatch input) {
        return service.attachSpecialWash(id, input);
    }

    /** 解除封存：专洗已完成才放行，件数同时还回在库。 */
    @PostMapping("/seals/{id}/release")
    public ContaminatedSeal release(@PathVariable Long id,
                                    @RequestParam(required = false) String releaser) {
        return service.release(id, releaser);
    }
}
