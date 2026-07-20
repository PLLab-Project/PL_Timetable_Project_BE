package com.example.pl_timetable_project.optimization.repository;

import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJob, Long> {

    @EntityGraph(attributePaths = "results")
    @Query("select j from OptimizationJob j where j.id = :id")
    Optional<OptimizationJob> findByIdWithResults(@Param("id") Long id);
}
