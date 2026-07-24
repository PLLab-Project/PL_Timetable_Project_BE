package com.example.pl_timetable_project.common.health;

import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "시스템", description = "서버 상태 확인")
public class HealthController {

    private final String version;
    private final String commit;

    public HealthController(
            @Value("${app.build.version:0.0.1-SNAPSHOT}") String version,
            @Value("${app.build.commit:unknown}") String commit) {
        this.version = version;
        this.commit = commit;
    }

    @Operation(
            summary = "서버 생존 확인",
            description = "현재 실행 중인 배포 버전과 Git 커밋을 반환합니다.")
    @GetMapping("/live")
    public ApiResponse<HealthLiveResponse> live() {
        return ApiResponse.success(new HealthLiveResponse("UP", version, commit));
    }
}
