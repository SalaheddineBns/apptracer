package com.apptracer.common.error;

/** Erreur portant sur un champ de la requête (élément de {@code errors[]}). */
public record ApiFieldError(String field, String message) {}
