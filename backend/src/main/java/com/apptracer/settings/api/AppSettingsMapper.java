package com.apptracer.settings.api;

import com.apptracer.settings.service.AppSettingsView;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsMapper {

  public AppSettingsResponse toResponse(AppSettingsView view) {
    AppSettingsView.Author a = view.lastModifiedBy();
    return new AppSettingsResponse(
        view.standardDayMinutes(),
        view.version(),
        view.lastModifiedAt(),
        a == null ? null : new SettingsAuthor(a.id(), a.firstName(), a.lastName()));
  }
}
