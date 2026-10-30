package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;

/**
 * Показатели в верхней части публичного дашборда.
 */
public record DashboardSummary(long openProjects, SpentTime currentMonth, SpentTime lastMonth) {
}
