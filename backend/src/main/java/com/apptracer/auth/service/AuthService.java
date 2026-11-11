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
  // Haché factice : une comparaison BCrypt est toujours effectuée, même pour un e-mail inconnu
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
    // RG-03-04 : comparaison BCrypt systématique pour ne pas révéler l'existence du compte (temps)
    // RG-03-03 : seul le haché BCrypt est comparé
    boolean passwordMatches =
        passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
    // RG-03-04 : e-mail inconnu ou mot de passe incorrect, même réponse
    // RG-03-05 : compte inactif, même réponse que des identifiants invalides
    if (user == null || !passwordMatches || !user.isActive()) {
      throw new InvalidCredentialsException();
    }
    UUID sessionId = UUID.randomUUID();
    // RG-03-06 : access token portant l'identifiant et le rôle
    String accessToken = accessTokens.issue(user.getId(), user.getRole(), sessionId);
    // RG-03-07 : refresh token stocké côté serveur
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
    // RG-03-05 : un compte inactif ne peut pas renouveler sa session
    if (!user.isActive()) {
      throw new InvalidCredentialsException();
    }
    // RG-03-07 : nouveau couple de jetons, même session
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
