package com.example.pl_timetable_project.common.health;

import com.example.pl_timetable_project.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping("/live")
    public ApiResponse<HealthLiveResponse> live() {
        return ApiResponse.success(new HealthLiveResponse("UP", "0.0.1-SNAPSHOT"));
    }
}
