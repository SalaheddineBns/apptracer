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
import com.apptracer.account.service.UserService;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceTest {

  private final UserRepository users = mock(UserRepository.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final UserService service =
      new UserService(users, encoder, mock(com.apptracer.account.service.SessionRevoker.class));

  private User create() {
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    return service.create(
        "  Jean@Example.com ", " Jean ", "Dupont", Role.MANAGER, "Motdepasse12345");
  }

  @Test
  @DisplayName("RG-03-01 : un e-mail déjà utilisé (quelle que soit la casse) est refusé")
  void duplicateEmailIsRejected() {
    when(users.existsByEmail("JEAN@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create("JEAN@example.com", "Jean", "Dupont", Role.USER, "Motdepasse12345"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-03-01");
              assertThat(e.getErrors()).extracting("field").containsExactly("email");
            });
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("RG-03-12 : le mot de passe initial défini par l'ADMIN est celui du compte")
  void initialPasswordIsTheOneProvided() {
    User user = create();
    assertThat(encoder.matches("Motdepasse12345", user.getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("RG-03-03 : le mot de passe est stocké haché en BCrypt, jamais en clair")
  void passwordIsHashed() {
    User user = create();
    assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain("Motdepasse12345");
  }

  @Test
  @DisplayName("RG-03-10 : le compte porte le rôle demandé ; actif à la création, e-mail nettoyé")
  void accountCreatedActiveWithRole() {
    User user = create();
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue()).isSameAs(user);
    assertThat(user.getRole()).isEqualTo(Role.MANAGER);
    assertThat(user.isActive()).isTrue();
    assertThat(user.getEmail()).isEqualTo("Jean@Example.com");
    assertThat(user.getFirstName()).isEqualTo("Jean");
  }

  @Test
  @DisplayName("Tri : une propriété non autorisée (haché du mot de passe) est refusée")
  void sortOnPasswordHashIsRejected() {
    assertThatThrownBy(() -> service.list(PageRequest.of(0, 20, Sort.by("passwordHash"))))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("getUser : compte inexistant, ressource introuvable")
  void unknownUserIsNotFound() {
    when(users.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
