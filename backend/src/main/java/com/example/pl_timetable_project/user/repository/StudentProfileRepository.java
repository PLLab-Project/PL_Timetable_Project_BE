package com.example.pl_timetable_project.user.repository;

import com.example.pl_timetable_project.user.entity.StudentProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByStudentNumber(String studentNumber);
}
