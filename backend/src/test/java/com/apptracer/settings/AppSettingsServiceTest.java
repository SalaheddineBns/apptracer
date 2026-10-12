package com.apptracer.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.settings.domain.AppSettings;
import com.apptracer.settings.repository.AppSettingsRepository;
import com.apptracer.settings.service.AppSettingsService;
import com.apptracer.settings.service.AppSettingsView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppSettingsServiceTest {

  private final AppSettingsRepository repository = mock(AppSettingsRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AppSettingsService service = new AppSettingsService(repository, users);
  private final Instant at = Instant.parse("2026-05-04T08:30:00Z");

  private static AppSettings settings(int minutes) throws Exception {
    Constructor<AppSettings> c = AppSettings.class.getDeclaredConstructor();
    c.setAccessible(true);
    AppSettings s = c.newInstance();
    Field f = AppSettings.class.getDeclaredField("standardDayMinutes");
    f.setAccessible(true);
    f.setInt(s, minutes);
    return s;
  }

  @BeforeEach
  void setUp() throws Exception {
    when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(settings(420)));
  }

  @Test
  @DisplayName("RG-00-02 : le service expose la journée standard en vigueur aux autres domaines")
  void exposesStandardDay() {
    assertThat(service.getStandardDayMinutes()).isEqualTo(420);
  }

  @Test
  @DisplayName("RG-06-03 : un non-ADMIN lit la journée standard sans auteur ni date")
  void nonAdminGetsNoLastModification() throws Exception {
    AppSettings s = settings(450);
    s.changeStandardDayMinutes(450, at, 7L);
    when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(s));

    AppSettingsView view = service.read(false);

    assertThat(view.standardDayMinutes()).isEqualTo(450);
    assertThat(view.lastModifiedAt()).isNull();
    assertThat(view.lastModifiedBy()).isNull();
    verifyNoInteractions(users); // H7 : le compte n'est pas même lu
  }

  @Test
  @DisplayName("RG-06-05 : l'ADMIN reçoit la date et l'auteur (identifiant, prénom, nom) seulement")
  void adminGetsLastModification() throws Exception {
    AppSettings s = settings(450);
    s.changeStandardDayMinutes(450, at, 7L);
    when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(s));
    User author = mock(User.class);
    when(author.getId()).thenReturn(7L);
    when(author.getFirstName()).thenReturn("Ada");
    when(author.getLastName()).thenReturn("Admin");
    when(users.findById(7L)).thenReturn(Optional.of(author));

    AppSettingsView view = service.read(true);

    assertThat(view.lastModifiedAt()).isEqualTo(at);
    assertThat(view.lastModifiedBy()).isEqualTo(new AppSettingsView.Author(7L, "Ada", "Admin"));
  }

  @Test
  @DisplayName("RG-06-05 : jamais modifié, l'ADMIN reçoit aussi des champs vides")
  void neverModifiedGivesNullsToAdmin() {
    AppSettingsView view = service.read(true);

    assertThat(view.standardDayMinutes()).isEqualTo(420); // RG-06-01
    assertThat(view.lastModifiedAt()).isNull();
    assertThat(view.lastModifiedBy()).isNull();
    org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).findById(anyLong());
  }
}
