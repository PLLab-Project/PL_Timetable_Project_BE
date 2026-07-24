package com.example.pl_timetable_project.completedcourse.repository;

import com.example.pl_timetable_project.completedcourse.CompletedCourseInputSource;
import com.example.pl_timetable_project.completedcourse.entity.CompletedCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletedCourseRepository extends JpaRepository<CompletedCourse, UUID> {

    List<CompletedCourse> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CompletedCourse> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndInputSourceAndSemesterAndCourseCodeAndSectionCode(
            UUID userId,
            CompletedCourseInputSource inputSource,
            String semester,
            String courseCode,
            String sectionCode);
}
