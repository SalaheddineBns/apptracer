package com.apptracer.settings.repository;

import com.apptracer.settings.domain.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Une seule ligne, d'identifiant {@link AppSettings#SINGLETON_ID}. */
public interface AppSettingsRepository extends JpaRepository<AppSettings, Short> {}
