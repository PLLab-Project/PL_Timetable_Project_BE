package com.example.pl_timetable_project.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginOtpChallengeTest {

    @Test
    void 만료와_재전송_가능_시간을_판단한다() {
        Instant now = Instant.parse("2026-07-24T03:00:00Z");
        LoginOtpChallenge challenge = new LoginOtpChallenge(
                "20260001", "20260001@daejin.ac.kr", "hashed-code",
                now.plusSeconds(300), now.plusSeconds(60));

        assertThat(challenge.isExpiredAt(now)).isFalse();
        assertThat(challenge.canResendAt(now)).isFalse();
        assertThat(challenge.canResendAt(now.plusSeconds(60))).isTrue();
        assertThat(challenge.isExpiredAt(now.plusSeconds(300))).isTrue();
    }

    @Test
    void 실패_횟수와_일회성_사용_상태를_기록한다() {
        Instant now = Instant.now();
        LoginOtpChallenge challenge = new LoginOtpChallenge(
                "20260001", "20260001@daejin.ac.kr", "hashed-code",
                now.plusSeconds(300), now.plusSeconds(60));

        challenge.recordFailure();
        challenge.consume(now);

        assertThat(challenge.failedAttempts()).isEqualTo(1);
        assertThat(challenge.isConsumed()).isTrue();
    }
}
