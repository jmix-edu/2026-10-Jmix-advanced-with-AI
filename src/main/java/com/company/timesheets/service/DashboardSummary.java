package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;

/**
 * Figures at the top of the public dashboard.
 */
public record DashboardSummary(long openProjects, SpentTime currentMonth, SpentTime lastMonth) {
}
