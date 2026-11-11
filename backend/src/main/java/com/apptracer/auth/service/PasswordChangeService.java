package com.apptracer.auth.service;

import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidCredentialsException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokens;

  public PasswordChangeService(
      UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokens = refreshTokens;
  }

  /**
   * RG-03-15: the signed-in user changes their password by providing the old one; their other
   * sessions are revoked. The format of the new password (RG-03-02) is checked by Bean Validation
   * on the DTO.
   */
  @Transactional
  public void changePassword(
      Long userId, UUID currentSessionId, String currentPassword, String newPassword) {
    User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
    // RG-03-15 : l'ancien mot de passe est exigé (409, et non 401, pour mettre le champ en erreur)
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw BusinessException.onField(
          "RG-03-15",
          "error.current-password.incorrect",
          "currentPassword",
          "error.current-password.incorrect");
    }
    // RG-03-03 : stocké haché en BCrypt ; RG-00-04 : updated_by = l'utilisateur lui-même
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    users.saveAndFlush(user);
    // RG-03-15 : le changement révoque les autres sessions, pas la session courante
    refreshTokens.revokeOtherSessions(userId, currentSessionId);
  }
}
