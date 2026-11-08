package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.account.service.DemoAccountsInitializer;
import com.apptracer.account.service.DemoAccountsProperties;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountsInitializerTest {

  private static final String PASSWORD = "Demo-accounts-pw-42";

  private final UserRepository users = mock(UserRepository.class);
  private final JobTitleRepository jobTitles = mock(JobTitleRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

  private DemoAccountsInitializer initializer(String password) {
    return new DemoAccountsInitializer(
        users,
        jobTitles,
        encoder,
        new DemoAccountsProperties(password),
        mock(TeamService.class),
        mock(TeamRepository.class),
        mock(TeamMemberRepository.class));
  }

  @Test
  void createsManagerDeveloperAndQaWithTheirJobTitles() {
    JobTitle developer = mock(JobTitle.class);
    JobTitle qa = mock(JobTitle.class);
    when(jobTitles.findByCode("DEVELOPER")).thenReturn(Optional.of(developer));
    when(jobTitles.findByCode("QA")).thenReturn(Optional.of(qa));

    assertThat(initializer(PASSWORD).createMissing()).isEqualTo(3);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users, times(3)).save(captor.capture());
    List<User> created = captor.getAllValues();
    assertThat(created)
        .extracting(User::getEmail, User::getRole, User::getJobTitle)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("manager@apptracer.local", Role.MANAGER, null),
            org.assertj.core.groups.Tuple.tuple("dev@apptracer.local", Role.USER, developer),
            org.assertj.core.groups.Tuple.tuple("qa@apptracer.local", Role.USER, qa));
    assertThat(created)
        .allSatisfy(
            user -> {
              assertThat(user.isActive()).isTrue();
              assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
            });
  }

  @Test
  void skipsAccountsWhoseEmailAlreadyExists() {
    when(users.existsByEmail("manager@apptracer.local")).thenReturn(true);
    when(users.existsByEmail("dev@apptracer.local")).thenReturn(true);
    when(users.existsByEmail("qa@apptracer.local")).thenReturn(true);

    assertThat(initializer(PASSWORD).createMissing()).isZero();

    verify(users, never()).save(any());
  }

  @Test
  void doesNothingWithoutDemoPassword() {
    assertThat(initializer(null).createMissing()).isZero();
    assertThat(initializer("  ").createMissing()).isZero();

    verify(users, never()).existsByEmail(any());
    verify(users, never()).save(any());
  }

  @Test
  void doesNothingWithAnInvalidDemoPassword() {
    assertThat(initializer("short").createMissing()).isZero();

    verify(users, never()).save(any());
  }
}
