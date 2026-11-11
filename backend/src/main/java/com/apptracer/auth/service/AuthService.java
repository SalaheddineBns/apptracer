package com.apptracer.auth.service;

import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.auth.domain.RefreshToken;
import com.apptracer.common.error.InvalidCredentialsException;
import com.apptracer.security.AccessTokenService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenService accessTokens;
  private final RefreshTokenService refreshTokens;
  // Dummy hash: a BCrypt comparison always runs, even for an unknown e-mail
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AccessTokenService accessTokens,
      RefreshTokenService refreshTokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.accessTokens = accessTokens;
    this.refreshTokens = refreshTokens;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public LoginResult login(String email, String password) {
    User user = users.findByEmail(email.trim()).orElse(null);
    // RG-03-04: systematic BCrypt comparison so as not to reveal whether the account exists
    // (timing). RG-03-03: only the BCrypt hash is compared
    boolean passwordMatches =
        passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
    // RG-03-04: unknown e-mail or wrong password, same response. RG-03-05: inactive account, same
    // response as invalid credentials
    if (user == null || !passwordMatches || !user.isActive()) {
      throw new InvalidCredentialsException();
    }
    UUID sessionId = UUID.randomUUID();
    // RG-03-06: access token carrying the identifier and the role
    String accessToken = accessTokens.issue(user.getId(), user.getRole(), sessionId);
    // RG-03-07: refresh token stored on the server
    String refreshToken = refreshTokens.issue(user.getId(), sessionId);
    return new LoginResult(accessToken, refreshToken, user);
  }

  @Transactional(readOnly = true)
  public User getCurrentUser(Long userId) {
    return users.findById(userId).orElseThrow(InvalidCredentialsException::new);
  }

  /**
   * RG-03-07: single-use rotation. Every failure (unknown, already used, revoked or expired token,
   * inactive account) raises the same exception. For an inactive account, the transaction is rolled
   * back.
   */
  @Transactional
  public LoginResult refreshSession(String rawRefreshToken) {
    RefreshToken old =
        refreshTokens.consume(rawRefreshToken).orElseThrow(InvalidCredentialsException::new);
    User user = users.findById(old.getUserId()).orElseThrow(InvalidCredentialsException::new);
    // RG-03-05: an inactive account cannot renew its session
    if (!user.isActive()) {
      throw new InvalidCredentialsException();
    }
    // RG-03-07: new token pair, same session
    String accessToken = accessTokens.issue(user.getId(), user.getRole(), old.getSessionId());
    String refreshToken = refreshTokens.issue(user.getId(), old.getSessionId());
    return new LoginResult(accessToken, refreshToken, user);
  }

  /** RG-03-08: signing out revokes the refresh token of the current session. */
  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokens.revoke(rawRefreshToken);
  }
}
