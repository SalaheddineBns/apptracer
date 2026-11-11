package com.apptracer.referential.service;

/**
 * Port for RG-02-07: a type is in use as soon as a time entry has recorded it, including a
 * logically deleted entry (H3). The real implementation is provided by SFD-01 ({@code timeentry}).
 */
public interface ActivityTypeUsageChecker {

  boolean isUsed(Long activityTypeId);
}
