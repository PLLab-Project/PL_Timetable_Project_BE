package com.example.pl_timetable_project.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "학기와 검증된 분반 키로 내 시간표를 생성하는 요청")
@Getter
@NoArgsConstructor
public class TimetableCreateRequest {

    @Schema(description = "사용자가 구분할 시간표 이름. 최대 120자", example = "2026-1 전공 시간표")
    @NotBlank
    @Size(max = 120)
    private String name;

    @Schema(description = "분반이 실제로 개설된 학기 ID", example = "2026-1")
    @NotBlank
    @Size(max = 20)
    private String semesterId;

    @Schema(
            description = "처음부터 포함할 분반 키 목록. 빈 목록이면 빈 시간표를 생성",
            example = "[{\"courseCode\":\"855121\",\"sectionCode\":\"01\"}]")
    @Valid
    private List<TimetableCourseRequest> sections = new ArrayList<>();

    public TimetableCreateRequest(String name, String semesterId, List<TimetableCourseRequest> sections) {
        this.name = name;
        this.semesterId = semesterId;
        this.sections = sections == null ? new ArrayList<>() : sections;
    }
}
