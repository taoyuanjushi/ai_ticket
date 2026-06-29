package com.example.hello_demo.vo;

public record DashboardStatsVO(
        Long ticketTotal,
        Long pendingCount,
        Long processingCount,
        Long doneCount,
        Long closedCount,
        Long highPriorityCount,
        Long urgentPriorityCount,
        Long aiSuggestionCount,
        Long aiAcceptedCount,
        Double aiAcceptanceRate
) {
}

