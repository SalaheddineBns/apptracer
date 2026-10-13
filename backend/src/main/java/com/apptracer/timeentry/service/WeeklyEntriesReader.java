package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture d'une semaine de saisies pour un compte donné en paramètre (jamais lu dans le contexte de
 * sécurité), réutilisée par le journal (SFD-01) et la vue équipe (SFD-04, RG-04-05).
 */
@Component
public class WeeklyEntriesReader {

  private final TimeEntryRepository entries;

  public WeeklyEntriesReader(TimeEntryRepository entries) {
    this.entries = entries;
  }

  /**
   * RG-01-11 : saisies non supprimées du compte qui coupent la semaine {@code [weekStart, weekStart
   * + 6 jours]}, PLAGE à cheval incluse (RG-01-13), type et projet embarqués même désactivés
   * (RG-02-05). {@code weekStart} est supposé être un lundi.
   */
  @Transactional(readOnly = true)
  public List<TimeEntry> read(Long userId, LocalDate weekStart) {
    return entries.findWeek(userId, weekStart, weekStart.plusDays(6));
  }
}
