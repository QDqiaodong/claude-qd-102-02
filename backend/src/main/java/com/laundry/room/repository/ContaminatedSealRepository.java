package com.laundry.room.repository;

import com.laundry.room.entity.ContaminatedSeal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaminatedSealRepository extends JpaRepository<ContaminatedSeal, Long> {

    boolean existsByCode(String code);

    List<ContaminatedSeal> findAllByOrderByIdDesc();

    List<ContaminatedSeal> findByLinenIdOrderByIdDesc(Long linenId);

    List<ContaminatedSeal> findByLinenIdAndStatus(Long linenId, String status);

    /** 行锁取这桩封存，专洗挂批 / 解除都要先拿到锁。 */
    @Query("select s from ContaminatedSeal s where s.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ContaminatedSeal> findByIdForUpdate(@Param("id") Long id);
}
