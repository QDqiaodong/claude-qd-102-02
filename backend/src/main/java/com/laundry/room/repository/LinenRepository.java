package com.laundry.room.repository;

import com.laundry.room.entity.Linen;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface LinenRepository extends JpaRepository<Linen, Long> {

    boolean existsByCode(String code);

    List<Linen> findAllByOrderByIdAsc();

    /** 行锁取布草台账行，扣在库 / 开封存抢单时用。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Linen l where l.id = :id")
    Optional<Linen> findByIdForUpdate(@Param("id") Long id);
}
