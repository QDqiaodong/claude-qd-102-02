package com.laundry.room.repository;

import com.laundry.room.entity.WashBatch;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WashBatchRepository extends JpaRepository<WashBatch, Long> {

    boolean existsByCode(String code);

    List<WashBatch> findByLinenIdAndSendDate(Long linenId, LocalDate sendDate);

    List<WashBatch> findByLinenIdAndStatusNot(Long linenId, String status);

    List<WashBatch> findBySealIdOrderByIdDesc(Long sealId);

    List<WashBatch> findAllByOrderByIdDesc();

    /** 行锁取批次：两个人同时点收工，第二个在这里排队，只成一个。 */
    @Query("select b from WashBatch b where b.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WashBatch> findByIdForUpdate(@Param("id") Long id);
}
