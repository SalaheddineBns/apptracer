package com.apptracer.settings.service;

import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.settings.domain.AppSettings;
import com.apptracer.settings.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paramètres de l'application (SFD-06). Point d'accès des autres domaines à la journée standard.
 */
@Service
public class AppSettingsService {

  private final AppSettingsRepository settings;
  private final UserRepository users;

  public AppSettingsService(AppSettingsRepository settings, UserRepository users) {
    this.settings = settings;
    this.users = users;
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
    AppSettings s = load();
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
