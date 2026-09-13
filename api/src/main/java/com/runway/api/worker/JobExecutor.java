package com.runway.api.worker;

import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import java.util.function.Consumer;

public interface JobExecutor {

    JobType supports();

    Outcome execute(Job job, Consumer<LogLine> logSink);

    record Outcome(boolean success, Integer exitCode, String errorMessage) {
    }
}
