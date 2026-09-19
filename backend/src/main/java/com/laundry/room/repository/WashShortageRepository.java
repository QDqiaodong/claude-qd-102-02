package com.laundry.room.repository;

import com.laundry.room.entity.WashShortage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WashShortageRepository extends JpaRepository<WashShortage, Long> {

    List<WashShortage> findAllByOrderByIdDesc();

    List<WashShortage> findByBatchIdAndStatus(Long batchId, String status);

    /** 这件布草还挂着的未结案追差（补回 / 报损确认都还没让短少件数落定）。 */
    List<WashShortage> findByLinenIdAndStatus(Long linenId, String status);

    /** 行锁取这张追差，补回入库 / 转报损 / 报损确认联动结案都要先拿到锁。 */
    @Query("select s from WashShortage s where s.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WashShortage> findByIdForUpdate(@Param("id") Long id);
}
