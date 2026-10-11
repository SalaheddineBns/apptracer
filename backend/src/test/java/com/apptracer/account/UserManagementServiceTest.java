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
import com.apptracer.account.service.SessionRevoker;
import com.apptracer.account.service.UserService;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserManagementServiceTest {

  private static final Long ADMIN_ID = 1L;
  private static final Long USER_ID = 2L;

  private final UserRepository users = mock(UserRepository.class);
  private final SessionRevoker revoker = mock(SessionRevoker.class);
  private final UserService service = new UserService(users, new BCryptPasswordEncoder(4), revoker);

  @BeforeEach
  void setUp() {
    when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
  }

  private User existing(Long id, Role role, boolean active) throws Exception {
    User u = new User("u" + id + "@example.com", "Prénom", "Nom", "hash", role);
    u.setActive(active);
    var f = User.class.getDeclaredField("id");
    f.setAccessible(true);
    f.set(u, id);
    when(users.findById(id)).thenReturn(Optional.of(u));
    return u;
  }

  @Test
  @DisplayName("RG-03-11 : seuls prénom, nom et rôle sont modifiés (e-mail et hash inchangés)")
  void updatesOnlyAllowedFields() throws Exception {
    User u = existing(USER_ID, Role.USER, true);

    User result = service.update(ADMIN_ID, USER_ID, " Jeanne ", "Martin", Role.MANAGER, 0);

    assertThat(result.getFirstName()).isEqualTo("Jeanne");
    assertThat(result.getLastName()).isEqualTo("Martin");
    assertThat(result.getRole()).isEqualTo(Role.MANAGER);
    assertThat(result.getEmail()).isEqualTo("u2@example.com");
    assertThat(result.getPasswordHash()).isEqualTo("hash");
    assertThat(result).isSameAs(u);
  }

  @Test
  @DisplayName("RG-03-13 : un ADMIN ne peut pas désactiver son propre compte")
  void adminCannotDeactivateSelf() throws Exception {
    existing(ADMIN_ID, Role.ADMIN, true);
    assertThatThrownBy(() -> service.deactivate(ADMIN_ID, ADMIN_ID))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-03-13"));
    verify(users, never()).saveAndFlush(any());
    verify(revoker, never()).revokeAllOnDeactivation(any());
  }

  @Test
  @DisplayName("RG-03-13 : un ADMIN ne peut pas retirer son propre rôle ADMIN")
  void adminCannotRemoveOwnAdminRole() throws Exception {
    existing(ADMIN_ID, Role.ADMIN, true);
    assertThatThrownBy(() -> service.update(ADMIN_ID, ADMIN_ID, "A", "B", Role.USER, 0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-03-13");
              assertThat(e.getErrors()).extracting("field").containsExactly("role");
            });
    verify(revoker, never()).revokeAllOnRoleChange(any());
  }

  @Test
  @DisplayName("RG-03-13 : un ADMIN peut modifier son prénom et son nom en gardant son rôle")
  void adminCanEditOwnNameKeepingRole() throws Exception {
    existing(ADMIN_ID, Role.ADMIN, true);
    User result = service.update(ADMIN_ID, ADMIN_ID, "Nouveau", "Nom", Role.ADMIN, 0);
    assertThat(result.getFirstName()).isEqualTo("Nouveau");
    verify(revoker, never()).revokeAllOnRoleChange(any());
  }

  @Test
  @DisplayName("RG-03-14 : désactiver un compte révoque tous ses refresh tokens")
  void deactivationRevokesSessions() throws Exception {
    existing(USER_ID, Role.USER, true);
    User result = service.deactivate(ADMIN_ID, USER_ID);
    assertThat(result.isActive()).isFalse();
    verify(revoker).revokeAllOnDeactivation(USER_ID);
  }

  @Test
  @DisplayName("RG-03-14 : changer le rôle révoque tous les refresh tokens")
  void roleChangeRevokesSessions() throws Exception {
    existing(USER_ID, Role.USER, true);
    service.update(ADMIN_ID, USER_ID, "A", "B", Role.MANAGER, 0);
    verify(revoker).revokeAllOnRoleChange(USER_ID);
  }

  @Test
  @DisplayName("RG-03-14 : une modification sans changement de rôle ne révoque rien")
  void noRoleChangeNoRevocation() throws Exception {
    existing(USER_ID, Role.USER, true);
    service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, 0);
    verify(revoker, never()).revokeAllOnRoleChange(any());
    verify(revoker, never()).revokeAllOnDeactivation(any());
  }

  @Test
  @DisplayName("Désactiver un compte déjà inactif est sans effet")
  void deactivatingInactiveAccountIsNoOp() throws Exception {
    existing(USER_ID, Role.USER, false);
    service.deactivate(ADMIN_ID, USER_ID);
    verify(users, never()).saveAndFlush(any());
    verify(revoker, never()).revokeAllOnDeactivation(any());
  }

  @Test
  @DisplayName("RG-03-11 : un ADMIN réactive un compte inactif")
  void reactivationActivatesAccount() throws Exception {
    existing(USER_ID, Role.USER, false);
    assertThat(service.reactivate(USER_ID).isActive()).isTrue();
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée lève un conflit de version")
  void staleVersionIsRejected() throws Exception {
    existing(USER_ID, Role.USER, true);
    assertThatThrownBy(() -> service.update(ADMIN_ID, USER_ID, "A", "B", Role.USER, 5))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Compte inexistant : ressource introuvable")
  void unknownAccountIsNotFound() {
    when(users.findById(42L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reactivate(42L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
