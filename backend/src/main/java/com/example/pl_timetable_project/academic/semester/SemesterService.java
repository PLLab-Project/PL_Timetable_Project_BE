package com.example.pl_timetable_project.academic.semester;

import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.semester.dto.SemesterDataVersionResponse;
import com.example.pl_timetable_project.academic.semester.dto.SemesterResponse;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SemesterService {

    private final SemesterQueryRepository repository;

    public SemesterService(SemesterQueryRepository repository) {
        this.repository = repository;
    }

    public List<SemesterResponse> getSemesters(boolean activeOnly) {
        return repository.findAll(activeOnly);
    }

    public SemesterResponse getSemester(String semesterId) {
        String normalizedId = TextQuery.required(semesterId, "학기 ID");
        return repository.findById(normalizedId)
                .orElseThrow(() -> notFound(normalizedId));
    }

    public SemesterDataVersionResponse getDataVersion(String semesterId) {
        String normalizedId = TextQuery.required(semesterId, "학기 ID");
        return repository.findDataVersion(normalizedId)
                .orElseThrow(() -> notFound(normalizedId));
    }

    private AcademicResourceNotFoundException notFound(String semesterId) {
        return new AcademicResourceNotFoundException(
                "학기를 찾을 수 없습니다. semesterId=" + semesterId);
    }
}
