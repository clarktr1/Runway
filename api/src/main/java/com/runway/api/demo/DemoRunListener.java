package com.runway.api.demo;

import com.runway.api.executions.ExecutionService.LogLine;

public interface DemoRunListener {

    void onStatus(DemoRunStatus status);

    void onLog(LogLine line);
}
