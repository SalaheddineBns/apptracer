package com.apptracer.referential.service;

import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Default implementation while SFD-01 does not exist: no time entries, so no type in use. {@link
 * Fallback}: it steps aside as soon as another implementation of the port is declared.
 */
@Component
@Fallback
class DefaultActivityTypeUsageChecker implements ActivityTypeUsageChecker {

  @Override
  public boolean isUsed(Long activityTypeId) {
    return false;
  }
}
