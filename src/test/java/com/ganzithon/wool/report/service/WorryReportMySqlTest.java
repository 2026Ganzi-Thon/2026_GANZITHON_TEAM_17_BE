package com.ganzithon.wool.report.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.report.entity.WorryReportReason;
import com.ganzithon.wool.report.repository.WorryReportRepository;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "WOOL_MYSQL_CONCURRENCY", matches = "true")
@org.springframework.boot.test.context.SpringBootTest(properties = {"spring.datasource.url=${WOOL_MYSQL_TEST_URL}", "spring.datasource.username=root", "spring.datasource.password=${WOOL_MYSQL_TEST_PASSWORD}", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "wool.scheduling.enabled=false"})
class WorryReportMySqlTest {
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired WorryReportRepository reports;
    @Autowired WorryReportService service;
    @Autowired com.ganzithon.wool.worry.service.WorryVisibilityService visibility;
    @Autowired com.ganzithon.wool.worry.service.WorryDeleteService deleteService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void 같은_신고자가_동시에_신고하면_한번만_저장한다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "신고동시청년"));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "신고동시선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> task = () -> { start.await(); try { service.report(senior, worry.getId(), WorryReportReason.AD); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder("success", "REPORT_409_002");
            assertThat(reports.count()).isEqualTo(1);
            assertThat(worries.findById(worry.getId()).orElseThrow().isHidden()).isTrue();
        } finally { pool.shutdownNow(); }
    }
    @Test void 공개범위_변경과_삭제가_신고와_겹쳐도_순서에_맞게_처리한다() throws Exception {
        for (String action : List.of("visibility", "delete")) {
            for (int n = 0; n < 5; n++) {
                String suffix = java.util.UUID.randomUUID().toString().substring(0, 6);
                User author = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "청" + suffix));
                User reporter = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "신" + suffix));
                Worry worry = worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
                var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
                try {
                    var report = pool.submit(() -> { start.await(); try { service.report(reporter, worry.getId(), WorryReportReason.AD); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } });
                    var change = pool.submit(() -> { start.await(); try { if (action.equals("visibility")) visibility.change(author.getId(), worry.getId(), WorryVisibility.SENIORS_ONLY); else deleteService.delete(author.getId(), worry.getId()); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } });
                    start.countDown(); String a = report.get(30, TimeUnit.SECONDS); String b = change.get(30, TimeUnit.SECONDS);
                    assertThat(a).isIn("success", action.equals("visibility") ? "WORRY_403_001" : "WORRY_404_001");
                    assertThat(b).isIn("success", "WORRY_404_001");
                    assertThat(reports.existsByWorryIdAndReporterId(worry.getId(), reporter.getId())).isEqualTo(a.equals("success"));
                } finally { pool.shutdownNow(); }
            }
        }
    }

    @Test void 먼저_신고된_고민은_범위변경이_잠금을_기다린_뒤_거부된다() throws Exception {
        User author = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "잠금작성자"));
        User reporter = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "잠금신고자"));
        Worry worry = worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        var pool = Executors.newFixedThreadPool(2); var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holder = new java.util.concurrent.atomic.AtomicLong();
        try {
            var first = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
                service.report(reporter, worry.getId(), WorryReportReason.AD);
                holder.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class)); held.countDown();
                try { if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("대기 초과"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return true;
            }));
            assertThat(held.await(15, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> { try { visibility.change(author.getId(), worry.getId(), WorryVisibility.SENIORS_ONLY); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } });
            boolean waiting = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!waiting && System.nanoTime() < deadline) {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON w.BLOCKING_THREAD_ID = t.THREAD_ID WHERE t.PROCESSLIST_ID = ?", Integer.class, holder.get());
                waiting = count != null && count > 0; if (!waiting) Thread.sleep(20);
            }
            assertThat(waiting).isTrue(); release.countDown(); assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo("WORRY_404_001");
        } finally { release.countDown(); pool.shutdownNow(); }
    }

}
