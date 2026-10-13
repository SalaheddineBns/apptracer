package com.apptracer.timeentry.repository;

import com.apptracer.timeentry.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {}
