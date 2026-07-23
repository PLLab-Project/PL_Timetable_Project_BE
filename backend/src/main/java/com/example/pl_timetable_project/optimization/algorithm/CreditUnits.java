package com.example.pl_timetable_project.optimization.algorithm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 탐색에서는 부동소수점 오차를 피하기 위해 학점을 1/100 단위 정수로 계산한다.
 */
public final class CreditUnits {

    private static final int SCALE = 2;

    private CreditUnits() {
    }

    public static int toUnits(BigDecimal credits) {
        return credits.setScale(SCALE, RoundingMode.UNNECESSARY)
                .movePointRight(SCALE)
                .intValueExact();
    }

    public static BigDecimal toCredits(int units) {
        return BigDecimal.valueOf(units, SCALE);
    }
}
