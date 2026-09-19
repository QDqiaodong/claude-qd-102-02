package com.laundry.room.repository;

import com.laundry.room.entity.LinenLoss;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinenLossRepository extends JpaRepository<LinenLoss, Long> {

    List<LinenLoss> findByLinenIdAndLossDate(Long linenId, LocalDate lossDate);

    /** 停用前查在途：这件布草还待确认的报损单。 */
    List<LinenLoss> findByLinenIdAndStatus(Long linenId, String status);

    List<LinenLoss> findAllByOrderByIdDesc();
}
