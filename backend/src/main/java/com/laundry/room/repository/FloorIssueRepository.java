package com.laundry.room.repository;

import com.laundry.room.entity.FloorIssue;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorIssueRepository extends JpaRepository<FloorIssue, Long> {

    List<FloorIssue> findByLinenIdAndFloorCodeAndIssueDate(Long linenId, String floorCode,
                                                          LocalDate issueDate);

    List<FloorIssue> findAllByOrderByIdDesc();
}
