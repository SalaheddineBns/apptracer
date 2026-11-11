package com.apptracer.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AppSettingsServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Europe/Paris"));
  private final AppSettingsRepository repository = mock(AppSettingsRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AppSettingsService service = new AppSettingsService(repository, users, CLOCK);
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
    when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(settings(480)));
  }

  @Test
  @DisplayName("RG-00-02 : le service expose la journée standard en vigueur aux autres domaines")
  void exposesStandardDay() {
    assertThat(service.getStandardDayMinutes()).isEqualTo(480);
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
    verifyNoInteractions(users); // H7: the account is not even read
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

    assertThat(view.standardDayMinutes()).isEqualTo(480); // RG-06-01
    assertThat(view.lastModifiedAt()).isNull();
    assertThat(view.lastModifiedBy()).isNull();
    org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).findById(anyLong());
  }

  private void stubAuthor() {
    User author = mock(User.class);
    when(author.getId()).thenReturn(9L);
    when(author.getFirstName()).thenReturn("Ada");
    when(author.getLastName()).thenReturn("Admin");
    when(users.findById(9L)).thenReturn(Optional.of(author));
    when(repository.saveAndFlush(any(AppSettings.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  @DisplayName(
      "RG-06-05 : un changement de valeur trace l'horloge applicative et l'auteur du jeton")
  void changeIsTraced() {
    stubAuthor();

    AppSettingsView view = service.update(450, 0, 9L);

    assertThat(view.standardDayMinutes()).isEqualTo(450);
    assertThat(view.lastModifiedAt()).isEqualTo(CLOCK.instant());
    assertThat(view.lastModifiedBy().id()).isEqualTo(9L);
    verify(repository).saveAndFlush(any(AppSettings.class));
  }

  @Test
  @DisplayName("RG-06-06 : une valeur identique n'écrit rien et conserve la trace précédente")
  void identicalValueIsNoOp() throws Exception {
    AppSettings s = settings(450);
    s.changeStandardDayMinutes(450, at, 9L);
    when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(s));
    stubAuthor();

    AppSettingsView view = service.update(450, 0, 9L);

    verify(repository, never()).saveAndFlush(any());
    assertThat(view.lastModifiedAt()).isEqualTo(at);
    assertThat(s.getUpdatedAt()).isEqualTo(at);
  }

  @Test
  @DisplayName("RG-06-06 : valeur identique sur une installation neuve, toujours aucune trace")
  void identicalValueOnFreshInstallation() {
    AppSettingsView view = service.update(480, 0, 9L);

    verify(repository, never()).saveAndFlush(any());
    assertThat(view.lastModifiedAt()).isNull();
    assertThat(view.lastModifiedBy()).isNull();
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée est refusée sans écriture")
  void staleVersionRejected() {
    assertThatThrownBy(() -> service.update(450, 5, 9L))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-06-04 : la modification n'écrit que la ligne de paramètres")
  void onlySettingsRowWritten() {
    stubAuthor();
    service.update(450, 0, 9L);
    verify(repository).findById(AppSettings.SINGLETON_ID);
    verify(repository).saveAndFlush(any(AppSettings.class));
    org.mockito.Mockito.verifyNoMoreInteractions(repository);
  }
}
