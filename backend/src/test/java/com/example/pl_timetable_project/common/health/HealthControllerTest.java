package com.example.pl_timetable_project.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    private final HealthController controller =
            new HealthController("1.4.0", "e72a719");

    @Test
    void returnsLiveStatus() {
        ApiResponse<HealthLiveResponse> response = controller.live();

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data().status()).isEqualTo("UP");
        assertThat(response.data().version()).isEqualTo("1.4.0");
        assertThat(response.data().commit()).isEqualTo("e72a719");
    }
}
