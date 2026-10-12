package com.apptracer.common.time;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propriétés {@code app.time.*}, validées au démarrage.
 *
 * @param zone fuseau métier, RG-00-01 (Europe/Paris)
 */
@Validated
@ConfigurationProperties(prefix = "app.time")
public record AppTimeProperties(@NotNull @DefaultValue("Europe/Paris") ZoneId zone) {}
