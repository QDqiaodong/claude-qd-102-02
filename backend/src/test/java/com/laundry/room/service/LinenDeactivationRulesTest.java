package com.laundry.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laundry.room.dto.BatchDonePayload;
import com.laundry.room.dto.BizException;
import com.laundry.room.entity.FloorIssue;
import com.laundry.room.entity.Linen;
import com.laundry.room.entity.LinenLoss;
import com.laundry.room.entity.WashBatch;
import com.laundry.room.entity.WashShortage;
import com.laundry.room.repository.ContaminatedSealRepository;
import com.laundry.room.repository.FloorIssueRepository;
import com.laundry.room.repository.LinenLossRepository;
import com.laundry.room.repository.LinenRepository;
import com.laundry.room.repository.WashBatchRepository;
import com.laundry.room.repository.WashShortageRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 停用 / 收工在途单据的纯服务层校验。
 * 行锁串行化本身由 MySQL 的 findByIdForUpdate 承担（@Lock(PESSIMISTIC_WRITE)），
 * 这里用 Mockito 钉死业务规则：在途单据逐张拦、停用后收工/补回绝不加库。
 */
@ExtendWith(MockitoExtension.class)
class LinenDeactivationRulesTest {

    @Mock LinenRepository linens;
    @Mock ContaminatedSealRepository seals;
    @Mock WashBatchRepository batches;
    @Mock FloorIssueRepository issues;
    @Mock LinenLossRepository losses;
    @Mock WashShortageRepository shortages;
    @InjectMocks LinenService linenService;

    private Linen linen;

    @BeforeEach
    void setup() {
        linen = new Linen();
        linen.id = 9L;
        linen.code = "LN-009";
        linen.name = "测试床单";
        linen.stock = 0;
        linen.status = "在用";
        when(linens.findByIdForUpdate(9L)).thenReturn(Optional.of(linen));
    }

    private void requestDeactivate() {
        Linen patch = new Linen();
        patch.status = "停用";
        linenService.update(9L, patch);
    }

    @Test
    void 送洗扣光后在库为零_有在途批次_停用仍被拦() {
        WashBatch b = new WashBatch();
        b.id = 3L;
        b.code = "WB-0003";
        b.linenId = 9L;
        b.quantity = 80;
        b.status = "待洗";
        when(batches.findByLinenIdAndStatusNot(9L, "已完成")).thenReturn(List.of(b));

        BizException ex = assertThrows(BizException.class, this::requestDeactivate);
        assertTrue(ex.getMessage().contains("WB-0003"), "要报清卡在哪一张单：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("待洗"), "要报清批次现在的状态");
        assertEquals("在用", linen.status, "停用不能落下");
    }

    @Test
    void 批次洗涤中_待烘干_都算在途() {
        for (String st : List.of("洗涤中", "待烘干")) {
            WashBatch b = new WashBatch();
            b.code = "WB-" + st;
            b.quantity = 10;
            b.status = st;
            when(batches.findByLinenIdAndStatusNot(9L, "已完成")).thenReturn(List.of(b));
            BizException ex = assertThrows(BizException.class, this::requestDeactivate);
            assertTrue(ex.getMessage().contains("WB-" + st), st + " 应拦住：" + ex.getMessage());
        }
    }

    @Test
    void 已送出没收回的楼层单_拦住停用() {
        FloorIssue i = new FloorIssue();
        i.id = 5L;
        i.linenId = 9L;
        i.floorCode = "8F";
        i.issueDate = LocalDate.of(2026, 9, 19);
        i.sendQty = 30;
        i.status = "已送出";
        when(issues.findByLinenIdAndStatus(9L, "已送出")).thenReturn(List.of(i));

        BizException ex = assertThrows(BizException.class, this::requestDeactivate);
        assertTrue(ex.getMessage().contains("8F"));
        assertTrue(ex.getMessage().contains("#5"), "要报清卡在哪一张楼层单");
    }

    @Test
    void 待确认报损_拦住停用() {
        LinenLoss loss = new LinenLoss();
        loss.id = 7L;
        loss.linenId = 9L;
        loss.lossDate = LocalDate.of(2026, 9, 19);
        loss.quantity = 4;
        loss.status = "待确认";
        when(losses.findByLinenIdAndStatus(9L, "待确认")).thenReturn(List.of(loss));

        BizException ex = assertThrows(BizException.class, this::requestDeactivate);
        assertTrue(ex.getMessage().contains("#7"));
        assertTrue(ex.getMessage().contains("待确认"));
    }

    @Test
    void 未结案追差_拦住停用() {
        WashShortage s = new WashShortage();
        s.id = 2L;
        s.code = "ZC-0002";
        s.linenId = 9L;
        s.shortQty = 8;
        when(shortages.findByLinenIdAndStatus(9L, WashShortageService.OPEN))
                .thenReturn(List.of(s));

        BizException ex = assertThrows(BizException.class, this::requestDeactivate);
        assertTrue(ex.getMessage().contains("ZC-0002"));
    }

    @Test
    void 在库还有件数_老规矩仍然拦() {
        linen.stock = 12;
        BizException ex = assertThrows(BizException.class, this::requestDeactivate);
        assertTrue(ex.getMessage().contains("12"));
    }

    @Test
    void 在库为零且四类在途单全清_停用才成立() {
        when(batches.findByLinenIdAndStatusNot(anyLong(), any())).thenReturn(List.of());
        when(issues.findByLinenIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(losses.findByLinenIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(shortages.findByLinenIdAndStatus(anyLong(), any())).thenReturn(List.of());

        requestDeactivate();
        assertEquals("停用", linen.status);
        verify(linens).save(linen);
    }

    // ---------- 停用落下之后，收工 / 补回不许再加回可领用 ----------

    @Test
    void 档案已停用_收工不加库且批次不完成() {
        WashBatchService batchService = new WashBatchService(batches, linens, shortages);
        linen.status = "停用";

        WashBatch batch = new WashBatch();
        batch.id = 1L;
        batch.code = "WB-1001";
        batch.linenId = 9L;
        batch.quantity = 50;
        batch.status = "待烘干";
        batch.washType = "常规";
        when(batches.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        when(linens.findByIdForUpdate(9L)).thenReturn(Optional.of(linen));

        BatchDonePayload payload = new BatchDonePayload();
        payload.returnQty = 50;
        BizException ex = assertThrows(BizException.class,
                () -> batchService.advance(1L, "done", payload));
        assertTrue(ex.getMessage().contains("停用"));
        verify(linens, never()).save(any(Linen.class));
        verify(batches, never()).save(any(WashBatch.class));
        assertEquals(0, linen.stock, "停用档案上不能冒出可领用件数");
        assertEquals("待烘干", batch.status, "收工回滚，批次仍是待烘干");
    }

    @Test
    void 档案在用_收工正常按回洗件数加库() {
        WashBatchService batchService = new WashBatchService(batches, linens, shortages);
        linen.status = "在用";
        linen.stock = 100;

        WashBatch batch = new WashBatch();
        batch.id = 1L;
        batch.linenId = 9L;
        batch.quantity = 50;
        batch.status = "待烘干";
        batch.washType = "常规";
        when(batches.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        when(linens.findByIdForUpdate(9L)).thenReturn(Optional.of(linen));
        when(shortages.save(any(WashShortage.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchDonePayload payload = new BatchDonePayload();
        payload.returnQty = 48;
        payload.duty = "8F";
        payload.founder = "孙姐";
        batchService.advance(1L, "done", payload);

        assertEquals(148, linen.stock, "还在用就按回洗 48 件加回在库");
        verify(linens).save(linen);
    }

    @Test
    void 档案已停用_追差补回也不加库() {
        com.laundry.room.repository.LinenLossRepository unusedLosses =
                org.mockito.Mockito.mock(LinenLossRepository.class);
        WashShortageService shortageService =
                new WashShortageService(shortages, linens, unusedLosses);
        linen.status = "停用";
        linen.stock = 0;

        WashShortage s = new WashShortage();
        s.id = 1L;
        s.code = "ZC-0001";
        s.linenId = 9L;
        s.shortQty = 6;
        s.status = WashShortageService.OPEN;
        when(shortages.findByIdForUpdate(1L)).thenReturn(Optional.of(s));
        when(linens.findByIdForUpdate(9L)).thenReturn(Optional.of(linen));

        BizException ex = assertThrows(BizException.class,
                () -> shortageService.replenish(1L));
        assertTrue(ex.getMessage().contains("停用"));
        verify(linens, never()).save(any(Linen.class));
        assertEquals(0, linen.stock);
        assertEquals(WashShortageService.OPEN, s.status, "追差不能被补回结案");
    }
}
