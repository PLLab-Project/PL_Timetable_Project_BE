package com.example.pl_timetable_project.timetable.repository;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = "timetableCourses")
    @Query("select t from Timetable t where t.id = :id")
    Optional<Timetable> findByIdWithCourses(@Param("id") Long id);
}
