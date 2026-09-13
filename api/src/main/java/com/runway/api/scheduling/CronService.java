package com.runway.api.scheduling;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class CronService {

    private final CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public Instant nextExecution(String cronExpression, Instant after) {
        Cron cron = parser.parse(cronExpression);
        cron.validate();
        return ExecutionTime.forCron(cron)
                .nextExecution(after.atZone(ZoneOffset.UTC))
                .map(zonedDateTime -> zonedDateTime.toInstant())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cron expression has no future executions: " + cronExpression));
    }
}
