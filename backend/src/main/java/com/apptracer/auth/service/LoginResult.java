package com.apptracer.auth.service;

import com.apptracer.account.domain.User;

/** Result of a successful sign-in: the token pair and the account. */
public record LoginResult(String accessToken, String refreshToken, User user) {}
