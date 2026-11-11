package com.apptracer.settings.service;

import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.settings.domain.AppSettings;
import com.apptracer.settings.repository.AppSettingsRepository;
import java.time.Clock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application settings (SFD-06). Entry point for the other domains to read the standard day. */
@Service
public class AppSettingsService {

  private final AppSettingsRepository settings;
  private final UserRepository users;
  private final Clock clock;

  public AppSettingsService(AppSettingsRepository settings, UserRepository users, Clock clock) {
    this.settings = settings;
    this.users = users;
    this.clock = clock;
  }

  /**
   * RG-00-02, RG-06-01: standard day in minutes, read from the database on each call (no cache). To
   * be called within the transaction that saves a time entry (SFD-01).
   */
  @Transactional(readOnly = true)
  public int getStandardDayMinutes() {
    return load().getStandardDayMinutes();
  }

  /**
   * RG-06-03: any signed-in user reads the standard day. H7 / RG-06-05: the author and date of the
   * last change are filled in for an ADMIN only.
   */
  @Transactional(readOnly = true)
  public AppSettingsView read(boolean includeLastModification) {
    return toView(load(), includeLastModification);
  }

  /**
   * RG-06-02: bounds and step are checked upstream (Bean Validation); the database constraint
   * remains the safety net. RG-06-03: ADMIN only (checked by the controller). RG-06-04: only the
   * settings row is written; no time entry is read or recalculated. The author comes from the token
   * and the date from the application clock, never from the request.
   */
  @Transactional
  public AppSettingsView update(int minutes, long version, long adminId) {
    AppSettings s = load();
    // Modification concurrente : la version lue par l'ADMIN n'est plus celle de la base
    if (s.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(AppSettings.class, s.getId());
    }
    // RG-06-06 : valeur identique, sans effet et non tracée (ni auteur, ni date, ni version)
    if (s.getStandardDayMinutes() == minutes) {
      return toView(s, true);
    }
    // RG-06-05, RG-00-04 : dernière modification tracée, seulement quand la valeur change
    s.changeStandardDayMinutes(minutes, clock.instant(), adminId);
    return toView(settings.saveAndFlush(s), true);
  }

  private AppSettingsView toView(AppSettings s, boolean includeLastModification) {
    if (!includeLastModification || s.getUpdatedAt() == null) {
      return new AppSettingsView(s.getStandardDayMinutes(), s.getVersion(), null, null);
    }
    User author =
        users
            .findById(s.getUpdatedBy())
            .orElseThrow(() -> new IllegalStateException("Auteur de la modification introuvable"));
    return new AppSettingsView(
        s.getStandardDayMinutes(),
        s.getVersion(),
        s.getUpdatedAt(),
        new AppSettingsView.Author(author.getId(), author.getFirstName(), author.getLastName()));
  }

  private AppSettings load() {
    // RG-06-01 : la ligne unique existe dès l'installation (migration)
    return settings
        .findById(AppSettings.SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Paramètres de l'application absents"));
  }
}
