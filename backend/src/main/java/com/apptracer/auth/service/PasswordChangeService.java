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
    // RG-03-15: the old password is required (409, not 401, so that the field is flagged in error)
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw BusinessException.onField(
          "RG-03-15",
          "error.current-password.incorrect",
          "currentPassword",
          "error.current-password.incorrect");
    }
    // RG-03-03: stored hashed with BCrypt; RG-00-04: updated_by = the user themselves
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    users.saveAndFlush(user);
    // RG-03-15: the change revokes the other sessions, not the current one
    refreshTokens.revokeOtherSessions(userId, currentSessionId);
  }
}
