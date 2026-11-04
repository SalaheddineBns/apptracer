package com.apptracer.timeentry.api;

import com.apptracer.common.api.DailyTotal;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.TimeEntryCommand;
import com.apptracer.timeentry.service.WeeklyTimeEntries;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class TimeEntryMapper {

  public TimeEntryCommand toCommand(CreateTimeEntryRequest r) {
    return new TimeEntryCommand(
        r.granularity(),
        r.startDate(),
        r.endDate(),
        r.startTime(),
        r.endTime(),
        r.activityTypeCode(),
        r.projectCode(),
        r.comment());
  }

  public TimeEntryCommand toCommand(UpdateTimeEntryRequest r) {
    return new TimeEntryCommand(
        r.granularity(),
        r.startDate(),
        r.endDate(),
        r.startTime(),
        r.endTime(),
        r.activityTypeCode(),
        r.projectCode(),
        r.comment());
  }

  public TimeEntryResponse toResponse(TimeEntry e) {
    var type = e.getActivityType();
    var project = e.getProject();
    return new TimeEntryResponse(
        e.getId(),
        e.getGranularity(),
        e.getStartDate(),
        // l'API ne renvoie endDate que pour RANGE
        e.getGranularity() == Granularity.RANGE ? e.getEndDate() : null,
        e.getStartTime(),
        e.getEndTime(),
        new ActivityTypeSummary(type.getCode(), type.getLabel(), type.getColor(), type.isActive()),
        project == null
            ? null
            : new ProjectSummary(project.getCode(), project.getName(), project.isActive()),
        e.getComment(),
        e.getDurationMinutes(),
        e.getVersion());
  }

  public WeeklyTimeEntriesResponse toResponse(WeeklyTimeEntries week) {
    return new WeeklyTimeEntriesResponse(
        week.weekStart(),
        week.weekEnd(),
        week.totalMinutes(),
        // RG-07-02, RG-07-03, RG-07-05 : durées par jour issues de WeeklyTotalCalculator
        IntStream.range(0, 7)
            .mapToObj(
                i -> new DailyTotal(week.weekStart().plusDays(i), week.minutesPerDay().get(i)))
            .toList(),
        week.entries().stream().map(this::toResponse).toList());
  }
}
