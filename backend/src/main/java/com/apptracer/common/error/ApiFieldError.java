package com.apptracer.common.error;

/** Error on a field of the request (an element of {@code errors[]}). */
public record ApiFieldError(String field, String message) {}
