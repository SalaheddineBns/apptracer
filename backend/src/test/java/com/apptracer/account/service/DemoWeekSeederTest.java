package com.apptracer.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoWeekSeederTest {

  @Test
  void mondayStartsWithPreviousFriday() {
    List<LocalDate> days = DemoWeekSeeder.previousWorkingDays(LocalDate.of(2026, 11, 9), 3);

    assertThat(days)
        .containsExactly(
            LocalDate.of(2026, 11, 4),
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6));
  }

  @Test
  void saturdaySkipsWeekend() {
    List<LocalDate> days = DemoWeekSeeder.previousWorkingDays(LocalDate.of(2026, 11, 7), 3);

    assertThat(days)
        .containsExactly(
            LocalDate.of(2026, 11, 4),
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6));
  }

  @Test
  void sundaySkipsWeekend() {
    List<LocalDate> days = DemoWeekSeeder.previousWorkingDays(LocalDate.of(2026, 11, 8), 3);

    assertThat(days)
        .containsExactly(
            LocalDate.of(2026, 11, 4),
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6));
  }

  @Test
  void crossesMonthBoundaryWithoutIncludingToday() {
    LocalDate today = LocalDate.of(2026, 10, 2);

    assertThat(DemoWeekSeeder.previousWorkingDays(today, 4))
        .containsExactly(
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 29),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 1));
  }

  @Test
  void returnsExactlyTenWorkingDaysOldestFirstAndNeverToday() {
    LocalDate today = LocalDate.of(2026, 11, 12);
    List<LocalDate> days = DemoWeekSeeder.previousWorkingDays(today, 10);

    assertThat(days).hasSize(10).doesNotContain(today).isSorted();
    assertThat(days)
        .allSatisfy(
            day -> {
              assertThat(day).isBefore(today);
              assertThat(day.getDayOfWeek())
                  .isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            });
    assertThat(days.getFirst()).isEqualTo(LocalDate.of(2026, 10, 29));
    assertThat(days.getLast()).isEqualTo(LocalDate.of(2026, 11, 11));
  }

  @Test
  void zeroWorkingDaysReturnsEmptyList() {
    assertThat(DemoWeekSeeder.previousWorkingDays(LocalDate.of(2026, 11, 12), 0))
        .isEmpty();
  }
}
