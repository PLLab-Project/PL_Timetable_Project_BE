package com.example.pl_timetable_project.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.web.context.WebApplicationContext;

/**
 * 같은 시간표를 동시에 PATCH하는 두 요청이 500이 아니라 409로 응답하는지
 * 회귀 테스트한다. Timetable.version(@Version)이 없던 시절에는 orphanRemoval
 * DELETE 단계에서 ObjectOptimisticLockingFailureException이 나면서 범용
 * Exception 핸들러(500)로 떨어졌다 — 지금은 GlobalExceptionHandler가 이
 * 예외를 명시적으로 잡아 409 Conflict로 응답한다.
 */
@SpringBootTest
@Testcontainers
class TimetableConcurrentUpdateSectionsTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TimetableService timetableService;

    private MockMvc mockMvc;
    private UUID userId;
    private Long timetableId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();

        userId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id) values (?)", userId);
        jdbcTemplate.update("""
                insert into semesters
                    (id, prepared_at, dataset_version, source_checksum, is_active, created_at)
                values ('2099-1', current_date, 'test', 'test', true, now())
                """);
        jdbcTemplate.update("""
                insert into courses (semester_id, course_code, name, category, credits)
                values
                    ('2099-1', 'A', 'course A', 'major', 3),
                    ('2099-1', 'B', 'course B', 'major', 3),
                    ('2099-1', 'C', 'course C', 'major', 3)
                """);
        jdbcTemplate.update("""
                insert into sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes)
                values
                    ('2099-1', 'A', '01', 'professor', '', false, '[]'),
                    ('2099-1', 'B', '01', 'professor', '', false, '[]'),
                    ('2099-1', 'C', '01', 'professor', '', false, '[]')
                """);

        TimetableResponse created = timetableService.createTimetable(
                userId,
                new TimetableCreateRequest(
                        "test", "2099-1",
                        List.of(new TimetableCourseRequest("A", "01"))));
        timetableId = created.id();
    }

    /**
     * 이 클래스는 @Transactional을 안 쓴다 — 자식 스레드가 자기 커넥션으로 여는
     * 트랜잭션이 테스트 메서드의(롤백 예정인) 트랜잭션 안 커밋 전 데이터를 못 볼
     * 수 있어서다(격리 수준). 대신 매 테스트 후 직접 정리해 다음 테스트의
     * setUp()과 고정 semester_id('2099-1')가 충돌하지 않게 한다.
     */
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from users where id = ?", userId);
        jdbcTemplate.update("delete from sections where semester_id = '2099-1'");
        jdbcTemplate.update("delete from courses where semester_id = '2099-1'");
        jdbcTemplate.update("delete from semesters where id = '2099-1'");
    }

    @Test
    void concurrentPatchSectionsRequestsReturn409InsteadOf500() throws Exception {
        String patchBody = """
                {"sections":[
                    {"courseCode":"A","sectionCode":"01"},
                    {"courseCode":"B","sectionCode":"01"}
                ]}
                """;

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/v1/timetables/{id}/sections", timetableId)
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(patchBody))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }
        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // 하나는 성공(200), 다른 하나는 버전 충돌로 409여야 한다 — 500은 절대
        // 나오면 안 된다.
        assertThat(statuses).doesNotContain(500);
        assertThat(statuses).contains(409);
        assertThat(statuses).contains(200);
    }

    /**
     * addCourse()는 timetableCourses 컬렉션에 새 행을 "추가"만 한다 — mappedBy
     * 컬렉션에 대한 단순 add()는 Hibernate가 소유 엔티티(Timetable)의 @Version을
     * 자동으로 증가시키지 않는다(FK를 자식 엔티티가 소유하므로 컬렉션 변경만으로는
     * 부모가 dirty로 표시되지 않음). TimetableService.addCourse()는 이제
     * entityManager.lock(timetable, OPTIMISTIC_FORCE_INCREMENT)로 버전 체크+증가를
     * 명시적으로 강제하므로, 서로 다른 분반을 동시에 추가해도(겹치는 분반이 전혀
     * 없어도) 늦게 flush되는 쪽은 409로 감지된다.
     */
    @Test
    void concurrentAddCourseRequestsReturn409InsteadOf500() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        String[] bodies = {
                "{\"courseCode\":\"B\",\"sectionCode\":\"01\"}",
                "{\"courseCode\":\"C\",\"sectionCode\":\"01\"}"
        };
        for (String body : bodies) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/timetables/{id}/sections", timetableId)
                                .with(authenticated())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }
        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // 하나는 성공(201), 다른 하나는 OPTIMISTIC_FORCE_INCREMENT 버전 충돌로
        // 409여야 한다 — 둘 다 조용히 201로 성공하면 안 된다.
        assertThat(statuses).doesNotContain(500);
        assertThat(statuses).contains(409);
        assertThat(statuses).contains(201);
    }

    /**
     * removeCourse()는 orphanRemoval DELETE를 발생시킨다 — 같은 행을 두 요청이
     * 동시에 지우면, 두 번째 DELETE는 영향받은 행이 0개라서 Hibernate의
     * StaleStateException이 발생한다. 이건 @Version 유무와 무관하게 Hibernate가
     * "PK로 DELETE했는데 영향받은 행이 기대(1개)와 다르면" 항상 검사하는
     * 동작이다 — 그래서 같은 분반을 동시에 삭제하는 요청은 자동으로 보호된다.
     */
    @Test
    void concurrentRemoveSameCourseRequestsReturn409InsteadOf500() throws Exception {
        TimetableResponse addedState = timetableService.addCourse(
                userId, timetableId, new TimetableCourseRequest("B", "01"));
        Long targetCourseId = addedState.sections().stream()
                .filter(c -> c.courseCode().equals("B"))
                .findFirst()
                .orElseThrow()
                .id();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/timetables/{id}/sections/{courseId}",
                                        timetableId, targetCourseId)
                                .with(authenticated())
                                .with(csrf()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }
        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(statuses).doesNotContain(500);
        assertThat(statuses).contains(409);
        assertThat(statuses).contains(200);
    }

    private RequestPostProcessor authenticated() {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "20990001");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(authentication);
    }
}
