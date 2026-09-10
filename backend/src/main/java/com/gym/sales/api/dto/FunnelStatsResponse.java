package com.gym.sales.api.dto;

import java.util.Map;

public record FunnelStatsResponse(
        Long totalLeads,
        Map<String, Long> stageCounts,
        Map<String, Long> lostReasonCounts,
        Double conversionRate
) {}
