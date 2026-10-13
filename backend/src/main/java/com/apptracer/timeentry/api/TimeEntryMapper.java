package com.apptracer.timeentry.api;

import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.TimeEntryCommand;
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
}
