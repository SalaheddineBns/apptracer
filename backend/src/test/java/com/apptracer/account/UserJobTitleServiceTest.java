package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.account.service.AccountTeamLinks;
import com.apptracer.account.service.SessionRevoker;
import com.apptracer.account.service.UserService;
import com.apptracer.common.error.BusinessException;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Contrôles de fonction du service des comptes (SFD-09). */
class UserJobTitleServiceTest {

  private static final Long ADMIN_ID = 1L;
  private static final Long USER_ID = 2L;

  private final UserRepository users = mock(UserRepository.class);
  private final SessionRevoker revoker = mock(SessionRevoker.class);
  private final AccountTeamLinks teamLinks = mock(AccountTeamLinks.class);
  private final JobTitleRepository jobTitles = mock(JobTitleRepository.class);
  private final UserService service =
      new UserService(users, new BCryptPasswordEncoder(4), revoker, teamLinks, jobTitles);

  @BeforeEach
  void setUp() {
    when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
  }

  private static <T> T withId(T entity, Long id) throws Exception {
    var f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
    return entity;
  }

  private JobTitle title(Long id, String code, boolean active) throws Exception {
    JobTitle t = withId(new JobTitle(code, code), id);
    t.setActive(active);
    when(jobTitles.findByCode(code)).thenReturn(Optional.of(t));
    return t;
  }

  private User existing(Role role, JobTitle carried) throws Exception {
    User u = withId(new User("u@example.com", "P", "N", "hash", role), USER_ID);
    u.setJobTitle(carried);
    when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(u));
    return u;
  }

  private static void assertJobTitleError(Throwable e, String rg) {
    assertThat(e)
        .isInstanceOfSatisfying(
            BusinessException.class,
            b -> {
              assertThat(b.getRgCode()).isEqualTo(rg);
              assertThat(b.getErrors()).extracting("field").containsExactly("jobTitleCode");
            });
  }

  @Test
  @DisplayName("RG-09-02 / RG-09-04 : crée un compte USER ou MANAGER avec une fonction active")
  void createsWithActiveJobTitle() throws Exception {
    JobTitle dev = title(10L, "DEVELOPER", true);
    User u =
        service.create("a@example.com", "A", "B", Role.MANAGER, "Motdepasse12345", "DEVELOPER");
    assertThat(u.getJobTitle()).isSameAs(dev);
  }

  @Test
  @DisplayName("RG-09-02 : la fonction est facultative à la création")
  void createsWithoutJobTitle() {
    User u = service.create("a@example.com", "A", "B", Role.USER, "Motdepasse12345", null);
    assertThat(u.getJobTitle()).isNull();
  }

  @Test
  @DisplayName(
      "RG-09-02 : refuse (409, champ jobTitleCode) une fonction pour un ADMIN, à la création")
  void createRejectsJobTitleForAdmin() throws Exception {
    title(10L, "DEVELOPER", true);
    assertThatThrownBy(
            () ->
                service.create(
                    "a@example.com", "A", "B", Role.ADMIN, "Motdepasse12345", "DEVELOPER"))
        .satisfies(e -> assertJobTitleError(e, "RG-09-02"));
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName(
      "RG-09-02 : refuse (409, champ jobTitleCode) une fonction pour un ADMIN, à la modification")
  void updateRejectsJobTitleForAdmin() throws Exception {
    title(10L, "DEVELOPER", true);
    existing(Role.ADMIN, null);
    assertThatThrownBy(
            () -> service.update(ADMIN_ID, USER_ID, "A", "B", Role.ADMIN, "DEVELOPER", 0))
        .satisfies(e -> assertJobTitleError(e, "RG-09-02"));
  }

  @Test
  @DisplayName("RG-09-06 : refuse une fonction inactive nouvellement attribuée, à la création")
  void createRejectsInactiveJobTitle() throws Exception {
    title(10L, "QA", false);
    assertThatThrownBy(
            () -> service.create("a@example.com", "A", "B", Role.USER, "Motdepasse12345", "QA"))
        .satisfies(e -> assertJobTitleError(e, "RG-09-06"));
  }

  @Test
  @DisplayName("RG-09-06 : refuse une fonction inconnue")
  void rejectsUnknownJobTitle() throws Exception {
    existing(Role.USER, null);
    when(jobTitles.findByCode("NOPE")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, "NOPE", 0))
        .satisfies(e -> assertJobTitleError(e, "RG-09-06"));
  }

  @Test
  @DisplayName("RG-09-06 : refuse une fonction inactive que le compte ne porte pas")
  void updateRejectsInactiveJobTitleNotCarried() throws Exception {
    title(10L, "QA", false);
    existing(Role.USER, null);
    assertThatThrownBy(() -> service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, "QA", 0))
        .satisfies(e -> assertJobTitleError(e, "RG-09-06"));
  }

  @Test
  @DisplayName("RG-09-06 : un compte garde la fonction inactive qu'il porte déjà")
  void updateKeepsCarriedInactiveJobTitle() throws Exception {
    JobTitle qa = title(10L, "QA", false);
    existing(Role.USER, qa);
    User u = service.update(ADMIN_ID, USER_ID, "A", "B", Role.MANAGER, "QA", 0);
    assertThat(u.getJobTitle()).isSameAs(qa);
  }

  @Test
  @DisplayName(
      "RG-09-06 / RG-09-04 : le compte peut passer à une fonction active ou n'en plus avoir")
  void updateChangesOrRemovesJobTitle() throws Exception {
    JobTitle qa = title(10L, "QA", false);
    JobTitle dev = title(11L, "DEVELOPER", true);
    existing(Role.USER, qa);
    assertThat(service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, "DEVELOPER", 0).getJobTitle())
        .isSameAs(dev);
    assertThat(service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, null, 0).getJobTitle())
        .isNull();
  }

  @Test
  @DisplayName("RG-09-03 : USER vers MANAGER ne touche pas la fonction")
  void roleChangeBetweenUserAndManagerKeepsJobTitle() throws Exception {
    JobTitle lead = title(10L, "TECH_LEAD", true);
    existing(Role.USER, lead);
    User u = service.update(ADMIN_ID, USER_ID, "A", "B", Role.MANAGER, "TECH_LEAD", 0);
    assertThat(u.getRole()).isEqualTo(Role.MANAGER);
    assertThat(u.getJobTitle()).isSameAs(lead);
  }

  @Test
  @DisplayName("RG-09-03 : le passage au rôle ADMIN retire la fonction, que USER ne rétablit pas")
  void promotionToAdminRemovesJobTitle() throws Exception {
    JobTitle dev = title(10L, "DEVELOPER", true);
    existing(Role.USER, dev);
    User u = service.update(ADMIN_ID, USER_ID, "A", "B", Role.ADMIN, null, 0);
    assertThat(u.getJobTitle()).isNull();
    User back = service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, null, 0);
    assertThat(back.getJobTitle()).isNull();
  }
}
