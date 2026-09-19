package com.laundry.room.repository;

import com.laundry.room.entity.FloorIssue;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorIssueRepository extends JpaRepository<FloorIssue, Long> {

    List<FloorIssue> findByLinenIdAndFloorCodeAndIssueDate(Long linenId, String floorCode,
                                                          LocalDate issueDate);

    /** 停用前查在途：这件布草已送出还没收回的楼层单。 */
    List<FloorIssue> findByLinenIdAndStatus(Long linenId, String status);

    List<FloorIssue> findAllByOrderByIdDesc();
}
