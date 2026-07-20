package com.example.pl_timetable_project.optimization.service;

import com.example.pl_timetable_project.optimization.algorithm.CandidateCourse;
import com.example.pl_timetable_project.optimization.algorithm.OptimizationConstraints;
import java.util.List;

/**
 * Job 이 커밋된 뒤 비동기로 편성 알고리즘을 실행하기 위한 이벤트.
 * 후보 강의 목록은 영속화 대상이 아니므로(별도 Course 도메인 없음) 이벤트에 담아 그대로 전달한다.
 */
public record OptimizationJobCreatedEvent(Long jobId, List<CandidateCourse> candidates,
                                           OptimizationConstraints constraints) {
}
