package com.runway.api.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class CronServiceTest {

    private final CronService cronService = new CronService();

    @Test
    void computesTheNextDailyExecution() {
        Instant after = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Instant next = cronService.nextExecution("0 2 * * *", after);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 1, 2, 2, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    void computesTheNextExecutionEverySixHours() {
        Instant after = ZonedDateTime.of(2026, 1, 1, 3, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Instant next = cronService.nextExecution("0 */6 * * *", after);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    void rejectsAnInvalidCronExpression() {
        assertThatThrownBy(() -> cronService.nextExecution("not a cron", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
