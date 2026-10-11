package com.apptracer.auth.api;

import com.apptracer.account.domain.User;
import com.apptracer.auth.service.LoginResult;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

  public CurrentUserResponse toResponse(User user) {
    return new CurrentUserResponse(
        user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole());
  }

  public AuthTokensResponse toResponse(LoginResult result) {
    return new AuthTokensResponse(
        result.accessToken(), result.refreshToken(), toResponse(result.user()));
  }
}
