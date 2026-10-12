package com.apptracer.settings.service;

import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.settings.domain.AppSettings;
import com.apptracer.settings.repository.AppSettingsRepository;
import java.time.Clock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paramètres de l'application (SFD-06). Point d'accès des autres domaines à la journée standard.
 */
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
   * RG-00-02, RG-06-01 : journée standard en minutes, lue en base à chaque appel (pas de cache). À
   * appeler dans la transaction de l'enregistrement d'une saisie (SFD-01).
   */
  @Transactional(readOnly = true)
  public int getStandardDayMinutes() {
    return load().getStandardDayMinutes();
  }

  /**
   * RG-06-03 : tout utilisateur connecté lit la journée standard. H7 / RG-06-05 : l'auteur et la
   * date de la dernière modification ne sont renseignés que pour un ADMIN.
   */
  @Transactional(readOnly = true)
  public AppSettingsView read(boolean includeLastModification) {
    return toView(load(), includeLastModification);
  }

  /**
   * RG-06-02 : bornes et pas contrôlés en amont (Bean Validation), la contrainte de base reste le
   * filet. RG-06-03 : réservé à l'ADMIN (contrôlé par le contrôleur). RG-06-04 : seule la ligne de
   * paramètres est écrite, aucune saisie n'est lue ni recalculée. L'auteur vient du jeton et la
   * date de l'horloge applicative, jamais de la requête.
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
