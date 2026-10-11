package com.apptracer.auth.service;

import com.apptracer.account.domain.User;

/** Résultat d'une connexion réussie : couple de jetons et compte. */
public record LoginResult(String accessToken, String refreshToken, User user) {}
