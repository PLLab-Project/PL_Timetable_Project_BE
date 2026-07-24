package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.exception.OptimizationTimeoutException;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.DoubleLinearExpr;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 필수 강의를 고정한 상태에서 선택 후보들에 대해 CP-SAT(OR-Tools) 모델을 세우고,
 * ScheduleScorer의 점수 공식을 목적함수로 직접 인코딩해 "최적해를 찾고, 이미 채택한 결과와
 * 강의 구성이 70% 이상 겹치면 그 해를 제외한 뒤 다시 최적해를 찾는" 과정을 반복해
 * 서로 다른 상위 3개 조합을 뽑는다.
 *
 * <p>하드 제약(반드시 지켜야 하는 것): 시간 충돌 배제, 학점 범위.
 * 그 외 "보조 변수를 그 정의식에 묶는" 제약들(addEquality/addMaxEquality/addAbsEquality 등)도
 * 하드 제약이지만, 이는 선호도가 아니라 "이 변수는 이런 값이어야 한다"는 순수한 정의일 뿐이다.
 * 소프트 제약(선호도)은 오직 {@link #buildObjective}에서 만드는 목적함수의 가중치로만 표현된다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleSearchService {

    private static final int TOP_N = 3;
    private static final double OVERLAP_THRESHOLD = 0.7;
    private static final int MAX_ITERATIONS = 50;
    private static final int MAX_SEARCH_WORKERS = 8;
    private static final int MINUTES_IN_DAY = 1440;

    private static final int DAYS_IN_WEEK = 7;
    private static final double ATTENDANCE_BONUS_PER_DAY = 10.0;
    private static final int GAP_BONUS_CAP_MINUTES = 300;
    private static final double GAP_BONUS_PER_MINUTE = 0.2;
    private static final double LUNCH_BONUS_PER_DAY = 15.0;
    private static final double CREDIT_DIFF_PENALTY_PER_CREDIT = 5.0;
    private static final double DAILY_OVERLOAD_PENALTY_PER_MINUTE = 1.0;

    static {
        Loader.loadNativeLibraries();
    }

    private final TopCombinationSelector topCombinationSelector;

    public List<ScheduleCombination> search(List<CandidateCourse> requiredCourses,
                                             List<CandidateCourse> optionalCandidates,
                                             OptimizationConstraints constraints) {
        int requiredCredit = requiredCourses.stream().mapToInt(CandidateCourse::creditUnits).sum();

        if (requiredCredit > constraints.maxCreditUnits()) {
            return List.of();
        }

        ModelContext context = buildModel(requiredCourses, optionalCandidates, constraints, requiredCredit);
        int searchWorkers = Math.max(1, Math.min(MAX_SEARCH_WORKERS, Runtime.getRuntime().availableProcessors()));
        long overallDeadlineNanos = System.nanoTime() + constraints.searchTimeLimitMillis() * 1_000_000L;

        List<ScheduleCombination> accepted = new ArrayList<>();
        boolean firstAttemptInfeasible = false;

        for (int attempt = 0; attempt < MAX_ITERATIONS && accepted.size() < TOP_N; attempt++) {
            long remainingMillis = (overallDeadlineNanos - System.nanoTime()) / 1_000_000L;
            if (remainingMillis <= 0) {
                break;
            }

            CpSolver solver = new CpSolver();
            solver.getParameters()
                    .setMaxTimeInSeconds(remainingMillis / 1000.0)
                    .setNumSearchWorkers(searchWorkers);
            CpSolverStatus status = solver.solve(context.model());

            if (status == CpSolverStatus.INFEASIBLE) {
                if (attempt == 0) {
                    firstAttemptInfeasible = true;
                }
                break;
            }
            if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
                // UNKNOWN/MODEL_INVALID: 남은 시간 안에 해를 하나도 못 찾았다.
                break;
            }

            boolean[] selectedMask = new boolean[context.vars().length];
            for (int i = 0; i < selectedMask.length; i++) {
                selectedMask[i] = solver.value(context.vars()[i]) == 1;
            }
            ScheduleCombination combination = toCombination(requiredCourses, optionalCandidates, requiredCredit, selectedMask);

            // 다음 탐색이 이번 해 근처에서 더 빨리 수렴하도록 힌트로 준 뒤,
            // 이번 해를 제외하는 제약을 추가해 다음 반복에서는 반드시 다른 해가 나오게 한다.
            applyHint(context.model(), context.vars(), selectedMask);
            excludeSolution(context.model(), context.vars(), selectedMask);

            boolean tooSimilarToAccepted = accepted.stream()
                    .anyMatch(existing -> topCombinationSelector.overlapRatio(existing.courses(), combination.courses())
                            >= OVERLAP_THRESHOLD);
            if (!tooSimilarToAccepted) {
                accepted.add(combination);
            }

            if (context.vars().length == 0) {
                // 선택 가능한 후보가 없어 필수 강의만으로 이루어진 조합 하나뿐이다.
                // 더 배제할 변수가 없으므로 반복을 계속해도 새로운 해를 찾을 수 없다.
                break;
            }
        }

        if (accepted.isEmpty() && !firstAttemptInfeasible) {
            throw new OptimizationTimeoutException(
                    "시간표 편성 탐색이 제한 시간(" + constraints.searchTimeLimitMillis() + "ms)을 초과했습니다.");
        }
        return accepted;
    }

    private void applyHint(CpModel model, BoolVar[] vars, boolean[] selectedMask) {
        model.clearHints();
        for (int i = 0; i < vars.length; i++) {
            model.addHint(vars[i], selectedMask[i] ? 1 : 0);
        }
    }

    private void excludeSolution(CpModel model, BoolVar[] vars, boolean[] selectedMask) {
        if (vars.length == 0) {
            return;
        }
        Literal[] literals = new Literal[vars.length];
        for (int i = 0; i < vars.length; i++) {
            literals[i] = selectedMask[i] ? vars[i].not() : vars[i];
        }
        model.addBoolOr(literals);
    }

    private ScheduleCombination toCombination(List<CandidateCourse> requiredCourses,
                                               List<CandidateCourse> optionalCandidates,
                                               int requiredCredit, boolean[] selectedMask) {
        List<CandidateCourse> selected = new ArrayList<>(requiredCourses);
        int totalCredit = requiredCredit;
        for (int i = 0; i < selectedMask.length; i++) {
            if (selectedMask[i]) {
                CandidateCourse course = optionalCandidates.get(i);
                selected.add(course);
                totalCredit += course.creditUnits();
            }
        }
        return new ScheduleCombination(List.copyOf(selected), totalCredit);
    }

    // ------------------------------------------------------------------
    // 모델 구성
    // ------------------------------------------------------------------

    private record ModelContext(CpModel model, BoolVar[] vars) {
    }

    private record OptionalDaySlot(int candidateIndex, int startMinutes, int endMinutes, boolean overlapsLunch) {
        int durationMinutes() {
            return endMinutes - startMinutes;
        }
    }

    private ModelContext buildModel(List<CandidateCourse> requiredCourses, List<CandidateCourse> optionalCandidates,
                                     OptimizationConstraints constraints, int requiredCredit) {
        CpModel model = new CpModel();
        BoolVar[] vars = new BoolVar[optionalCandidates.size()];
        for (int i = 0; i < vars.length; i++) {
            vars[i] = model.newBoolVar("course_" + i);
        }

        // ---- 하드 제약: 시간 충돌 ----
        for (int i = 0; i < optionalCandidates.size(); i++) {
            for (int j = i + 1; j < optionalCandidates.size(); j++) {
                if (optionalCandidates.get(i).conflictsWith(optionalCandidates.get(j))) {
                    model.addAtMostOne(new Literal[] {vars[i], vars[j]});
                }
            }
        }

        // ---- 하드 제약: 학점 범위 ----
        LinearExprBuilder creditBuilder = LinearExpr.newBuilder().add(requiredCredit);
        long[] credits = optionalCandidates.stream().mapToLong(CandidateCourse::creditUnits).toArray();
        if (vars.length > 0) {
            creditBuilder.addWeightedSum(vars, credits);
        }
        LinearExpr totalCreditExpr = creditBuilder.build();
        model.addLinearConstraint(
                totalCreditExpr, constraints.minCreditUnits(), constraints.maxCreditUnits());

        // ---- 요일별 슬롯 집계(필수=상수, 선택=변수) ----
        Map<DayOfWeek, Integer> requiredMinutesByDay = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> requiredMinStartByDay = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> requiredMaxEndByDay = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Boolean> requiredLunchConflictByDay = new EnumMap<>(DayOfWeek.class);
        for (CandidateCourse course : requiredCourses) {
            for (CourseTimeSlot slot : course.timeSlots()) {
                DayOfWeek day = slot.dayOfWeek();
                int start = toMinutes(slot.startTime());
                int end = toMinutes(slot.endTime());
                requiredMinutesByDay.merge(day, end - start, Integer::sum);
                requiredMinStartByDay.merge(day, start, Math::min);
                requiredMaxEndByDay.merge(day, end, Math::max);
                if (overlapsLunch(start, end, constraints)) {
                    requiredLunchConflictByDay.put(day, true);
                }
            }
        }

        Map<DayOfWeek, List<OptionalDaySlot>> optionalSlotsByDay = new EnumMap<>(DayOfWeek.class);
        for (int i = 0; i < optionalCandidates.size(); i++) {
            for (CourseTimeSlot slot : optionalCandidates.get(i).timeSlots()) {
                int start = toMinutes(slot.startTime());
                int end = toMinutes(slot.endTime());
                optionalSlotsByDay.computeIfAbsent(slot.dayOfWeek(), d -> new ArrayList<>())
                        .add(new OptionalDaySlot(i, start, end, overlapsLunch(start, end, constraints)));
            }
        }

        Set<DayOfWeek> relevantDays = new HashSet<>(requiredMinutesByDay.keySet());
        relevantDays.addAll(optionalSlotsByDay.keySet());

        List<IntVar> objectiveVars = new ArrayList<>();
        List<Double> objectiveCoeffs = new ArrayList<>();
        LinearExprBuilder totalFreeMinutesBuilder = LinearExpr.newBuilder();

        for (DayOfWeek day : relevantDays) {
            List<OptionalDaySlot> optionalSlots = optionalSlotsByDay.getOrDefault(day, List.of());
            boolean requiredOnDay = requiredMinutesByDay.containsKey(day);

            // dayUsed: 이 요일에 수업이 있는가. 등교일수 최소화(=dayUsed 합 최소화)에 사용된다.
            BoolVar dayUsed = model.newBoolVar("dayUsed_" + day);
            if (requiredOnDay) {
                model.addEquality(dayUsed, 1);
            }
            for (OptionalDaySlot slot : optionalSlots) {
                model.addImplication(vars[slot.candidateIndex()], dayUsed);
            }
            if (!requiredOnDay && !optionalSlots.isEmpty()) {
                // 반대 방향도 강제해야 한다: 이 날 선택된 후보가 하나도 없으면 dayUsed=0.
                // 이 제약이 없으면 dayUsed를 "거짓으로" 1로 두어도 아무 페널티 없이
                // dayMinStart/dayMaxEnd/lunchSecured 가 전부 자유 변수가 되어 목적함수를
                // (실제로 존재하지 않는 공강시간 상쇄, 점심 보너스 편취 등으로) 부풀릴 수 있다.
                Literal[] daySlotLiterals = optionalSlots.stream()
                        .map(slot -> (Literal) vars[slot.candidateIndex()])
                        .toArray(Literal[]::new);
                model.addBoolOr(daySlotLiterals).onlyEnforceIf(dayUsed);
            }
            objectiveVars.add(dayUsed);
            objectiveCoeffs.add(-ATTENDANCE_BONUS_PER_DAY);

            // 그날 총 수업시간 = 필수(상수) + 선택된 후보들의 시간 합
            LinearExprBuilder dailyMinutesBuilder = LinearExpr.newBuilder()
                    .add(requiredMinutesByDay.getOrDefault(day, 0));
            if (!optionalSlots.isEmpty()) {
                BoolVar[] slotVars = optionalSlots.stream()
                        .map(s -> vars[s.candidateIndex()]).toArray(BoolVar[]::new);
                long[] slotDurations = optionalSlots.stream().mapToLong(OptionalDaySlot::durationMinutes).toArray();
                dailyMinutesBuilder.addWeightedSum(slotVars, slotDurations);
            }
            LinearExpr dailyMinutesExpr = dailyMinutesBuilder.build();

            // dayMinStart/dayMaxEnd: 그날 실제로 선택된 수업들의 시작 최솟값/종료 최댓값.
            // "<= 모든 활성 슬롯의 시작" / ">= 모든 활성 슬롯의 종료" 제약만 걸어두면,
            // 목적함수가 공강시간(=span-수업시간)을 최소화하려는 압력 때문에
            // 최적해에서는 이 값들이 정확히 실제 최소/최대값으로 수렴한다(그 외에는 이 변수들이
            // 관여하는 항이 없어 이 압력을 방해할 요인이 없다). 요일 내 강의는 시간 충돌 제약으로
            // 이미 겹치지 않음이 보장되므로, (dayMaxEnd-dayMinStart)-총수업시간 은 곧
            // "연속 수업 사이 공강시간의 합"과 정확히 같다(첫 수업 전/마지막 수업 후는 제외).
            IntVar dayMinStart = model.newIntVar(0, MINUTES_IN_DAY, "minStart_" + day);
            IntVar dayMaxEnd = model.newIntVar(0, MINUTES_IN_DAY, "maxEnd_" + day);
            model.addEquality(dayMinStart, 0).onlyEnforceIf(dayUsed.not());
            model.addEquality(dayMaxEnd, 0).onlyEnforceIf(dayUsed.not());
            if (requiredOnDay) {
                model.addLessOrEqual(dayMinStart, requiredMinStartByDay.get(day));
                model.addGreaterOrEqual(dayMaxEnd, requiredMaxEndByDay.get(day));
            }
            for (OptionalDaySlot slot : optionalSlots) {
                BoolVar v = vars[slot.candidateIndex()];
                model.addLessOrEqual(dayMinStart, slot.startMinutes()).onlyEnforceIf(v);
                model.addGreaterOrEqual(dayMaxEnd, slot.endMinutes()).onlyEnforceIf(v);
            }
            totalFreeMinutesBuilder.add(dayMaxEnd);
            totalFreeMinutesBuilder.addTerm(dayMinStart, -1);
            totalFreeMinutesBuilder.addTerm(dailyMinutesExpr, -1);

            // 하루 최대 수업시간 초과분 = max(0, 그날 수업시간 - maxDailyClassMinutes)
            int maxPossibleMinutes = requiredMinutesByDay.getOrDefault(day, 0)
                    + optionalSlots.stream().mapToInt(OptionalDaySlot::durationMinutes).sum();
            IntVar overMinutes = model.newIntVar(0, Math.max(0, maxPossibleMinutes), "over_" + day);
            LinearExpr overExpr = LinearExpr.affine(dailyMinutesExpr, 1, -constraints.maxDailyClassMinutes());
            model.addMaxEquality(overMinutes, new LinearArgument[] {LinearExpr.constant(0), overExpr});
            objectiveVars.add(overMinutes);
            objectiveCoeffs.add(-DAILY_OVERLOAD_PENALTY_PER_MINUTE);

            // 점심시간 확보: 필수 강의가 이미 점심시간과 겹치면 이 요일은 절대 확보될 수 없어
            // 변수 자체를 만들지 않는다(=보너스 0으로 고정한 것과 동일).
            boolean requiredLunchConflict = requiredLunchConflictByDay.getOrDefault(day, false);
            if (!requiredLunchConflict) {
                BoolVar lunchSecured = model.newBoolVar("lunchSecured_" + day);
                model.addImplication(dayUsed.not(), lunchSecured.not());
                for (OptionalDaySlot slot : optionalSlots) {
                    if (slot.overlapsLunch()) {
                        model.addImplication(vars[slot.candidateIndex()], lunchSecured.not());
                    }
                }
                objectiveVars.add(lunchSecured);
                objectiveCoeffs.add(LUNCH_BONUS_PER_DAY);
            }
        }

        // ---- 공강시간 보너스: max(0, 300분 - 주간 총 공강시간) ----
        LinearExpr totalFreeMinutesExpr = totalFreeMinutesBuilder.build();
        IntVar gapBonus = model.newIntVar(0, GAP_BONUS_CAP_MINUTES, "gapBonus");
        LinearExpr gapBonusCandidate = LinearExpr.affine(totalFreeMinutesExpr, -1, GAP_BONUS_CAP_MINUTES);
        model.addMaxEquality(gapBonus, new LinearArgument[] {LinearExpr.constant(0), gapBonusCandidate});
        objectiveVars.add(gapBonus);
        objectiveCoeffs.add(GAP_BONUS_PER_MINUTE);

        // ---- 목표학점과의 차이(절댓값) ----
        int totalOptionalCredit =
                optionalCandidates.stream().mapToInt(CandidateCourse::creditUnits).sum();
        int creditDiffBound =
                requiredCredit + totalOptionalCredit + Math.abs(constraints.targetCreditUnits()) + 1;
        IntVar absCreditDiff = model.newIntVar(0, creditDiffBound, "absCreditDiff");
        LinearExpr creditDiffExpr =
                LinearExpr.affine(totalCreditExpr, 1, -constraints.targetCreditUnits());
        model.addAbsEquality(absCreditDiff, creditDiffExpr);
        objectiveVars.add(absCreditDiff);
        objectiveCoeffs.add(-CREDIT_DIFF_PENALTY_PER_CREDIT);

        buildObjective(model, objectiveVars, objectiveCoeffs);

        return new ModelContext(model, vars);
    }

    /**
     * ScheduleScorer의 점수식을 그대로 옮긴 목적함수:
     * (7 - 등교일수)*10 + min(300, 300-공강시간)*0.2 + 점심확보일수*15
     * - |총학점-목표학점|*5 - 하루초과시간*1
     * 가중치가 정수가 아니어서(0.2 등) DoubleLinearExpr로 실수 계수를 그대로 사용한다
     * (정수 배율로 스케일링할 필요가 없다).
     */
    private void buildObjective(CpModel model, List<IntVar> objectiveVars, List<Double> objectiveCoeffs) {
        IntVar[] varsArray = objectiveVars.toArray(new IntVar[0]);
        double[] coeffsArray = objectiveCoeffs.stream().mapToDouble(Double::doubleValue).toArray();
        double offset = DAYS_IN_WEEK * ATTENDANCE_BONUS_PER_DAY;
        model.maximize(new DoubleLinearExpr(varsArray, coeffsArray, offset));
    }

    private boolean overlapsLunch(int startMinutes, int endMinutes, OptimizationConstraints constraints) {
        int lunchStart = toMinutes(constraints.lunchTimeStart());
        int lunchEnd = toMinutes(constraints.lunchTimeEnd());
        return startMinutes < lunchEnd && lunchStart < endMinutes;
    }

    private int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
