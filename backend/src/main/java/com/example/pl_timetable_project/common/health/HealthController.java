package com.example.pl_timetable_project.common.health;

import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "시스템", description = "서버 상태 확인")
public class HealthController {

    @Operation(summary = "서버 생존 확인")
    @GetMapping("/live")
    public ApiResponse<HealthLiveResponse> live() {
        return ApiResponse.success(new HealthLiveResponse("UP", "0.0.1-SNAPSHOT"));
    }
}
