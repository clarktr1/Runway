package com.runway.api.worker;

import com.runway.api.executions.CancellationToken;
import com.runway.api.executions.ExecutionService.LogLine;
import com.runway.api.jobs.Job;
import com.runway.api.jobs.JobType;
import java.util.function.Consumer;

public interface JobExecutor {

    JobType supports();

    Outcome execute(Job job, Consumer<LogLine> logSink, CancellationToken cancellationToken);

    enum Result {
        SUCCESS,
        FAILED,
        TIMEOUT,
        CANCELLED
    }

    record Outcome(Result result, Integer exitCode, String errorMessage) {
    }
}
