package com.ganzithon.wool.report.scheduler;

import com.ganzithon.wool.IntegrationTestSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// 스케줄러는 설정으로 켜고 끈다. 테스트에서는 꺼져 있고, 켜면 1분 간격 작업이 등록된다.
class RestrictionLiftSchedulerTest extends IntegrationTestSupport {

    @Autowired private ApplicationContext context;

    @Test
    void 테스트_설정에서는_스케줄러가_만들어지지_않는다() {
        assertThat(context.getBeanNamesForType(RestrictionLiftScheduler.class)).isEmpty();
    }

    @Nested
    @TestPropertySource(properties = {
            "wool.scheduling.enabled=true",
            "spring.datasource.url=jdbc:h2:mem:test_scheduler;MODE=MySQL"
    })
    class Enabled {

        @Autowired private ApplicationContext enabledContext;

        @Test
        void 켜면_스케줄러가_만들어지고_1분마다_실행되는_작업이_실제로_등록된다() {
            assertThat(enabledContext.getBeanNamesForType(RestrictionLiftScheduler.class)).hasSize(1);
            // @EnableScheduling이 없으면 이 후처리기 Bean이 없어서 여기서 실패한다.
            ScheduledAnnotationBeanPostProcessor processor = enabledContext.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks())
                    .map(ScheduledTask::getTask)
                    .filteredOn(task -> task instanceof FixedDelayTask)
                    .map(task -> (FixedDelayTask) task)
                    .anySatisfy(task -> {
                        assertThat(task.getIntervalDuration().toMillis()).isEqualTo(60_000L);
                        assertThat(task.getRunnable().toString()).contains("RestrictionLiftScheduler.run");
                    });
        }
    }
}
