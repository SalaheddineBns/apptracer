package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.WeeklyTotalCalculator;
import com.apptracer.timeentry.service.WeeklyTotalCalculator.WeekTotals;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyTotalCalculatorTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 10, 5); // lundi
  private static final ActivityType TYPE = new ActivityType("REUNION", "R", false, TagColor.WARN);
  private final WeeklyTotalCalculator calc = new WeeklyTotalCalculator();

  private static TimeEntry hour(LocalDate d, int from, int to, int minutes) {
    TimeEntry e = new TimeEntry(1L, TYPE);
    e.setPeriod(Granularity.HOUR, d, d, LocalTime.of(from, 0), LocalTime.of(to, 0));
    e.setDuration(minutes, 420);
    return e;
  }

  private static TimeEntry day(LocalDate d, int std) {
    TimeEntry e = new TimeEntry(1L, TYPE);
    e.setPeriod(Granularity.DAY, d, d, null, null);
    e.setDuration(std, std);
    return e;
  }

  private static TimeEntry range(LocalDate from, LocalDate to, int frozenStd, int duration) {
    TimeEntry e = new TimeEntry(1L, TYPE);
    e.setPeriod(Granularity.RANGE, from, to, null, null);
    e.setDuration(duration, frozenStd);
    return e;
  }

  @Test
  @DisplayName("CA-01-09 : 90 min et 7 h dans la semaine donnent 510 min, soit 8,5 h")
  void ninetyMinutesAndSevenHours() {
    WeekTotals t =
        calc.compute(List.of(hour(WEEK, 9, 10, 90), hour(WEEK.plusDays(1), 9, 10, 420)), WEEK);
    assertThat(t.totalMinutes()).isEqualTo(510);
    assertThat(t.minutesPerDay()).containsExactly(90, 420, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName("RG-01-13 : semaine vide, sept jours à zéro")
  void emptyWeek() {
    WeekTotals t = calc.compute(List.of(), WEEK);
    assertThat(t.totalMinutes()).isZero();
    assertThat(t.minutesPerDay()).containsExactly(0, 0, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName(
      "RG-01-13 : PLAGE du jeudi 08/10 au mardi 13/10 = 2 jours ouvrés dans chaque semaine")
  void rangeAcrossTwoWeeks() {
    TimeEntry r = range(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 13), 420, 4 * 420);
    WeekTotals first = calc.compute(List.of(r), WEEK);
    assertThat(first.totalMinutes()).isEqualTo(840); // CA-04-07 : jeudi + vendredi
    assertThat(first.minutesPerDay()).containsExactly(0, 0, 0, 420, 420, 0, 0);
    WeekTotals second = calc.compute(List.of(r), WEEK.plusDays(7));
    assertThat(second.totalMinutes()).isEqualTo(840); // lundi + mardi
    assertThat(second.minutesPerDay()).containsExactly(420, 420, 0, 0, 0, 0, 0);
    // la somme des semaines égale la durée enregistrée
    assertThat(first.totalMinutes() + second.totalMinutes()).isEqualTo(r.getDurationMinutes());
  }

  @Test
  @DisplayName("RG-01-13 : PLAGE entièrement sur un week-end compte 0")
  void rangeOnWeekendOnly() {
    TimeEntry r = range(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), 420, 0);
    assertThat(calc.compute(List.of(r), WEEK).totalMinutes()).isZero();
  }

  @Test
  @DisplayName("RG-01-08 : JOURNEE un samedi compte une journée standard dans la semaine")
  void dayOnSaturday() {
    WeekTotals t = calc.compute(List.of(day(LocalDate.of(2026, 10, 10), 420)), WEEK);
    assertThat(t.totalMinutes()).isEqualTo(420);
    assertThat(t.minutesPerDay().get(5)).isEqualTo(420);
  }

  @Test
  @DisplayName("RG-04-05 : une PLAGE utilise la journée standard figée sur la saisie")
  void rangeUsesFrozenStandardDay() {
    TimeEntry r = range(WEEK, WEEK.plusDays(4), 450, 5 * 450);
    assertThat(calc.compute(List.of(r), WEEK).totalMinutes()).isEqualTo(5 * 450);
  }

  @Test
  @DisplayName("RG-01-13 : dimanche et lundi aux bornes de la semaine")
  void weekBounds() {
    TimeEntry sunday = hour(WEEK.plusDays(6), 9, 10, 60);
    TimeEntry monday = hour(WEEK, 9, 10, 60);
    TimeEntry previousSunday = hour(WEEK.minusDays(1), 9, 10, 60);
    TimeEntry nextMonday = hour(WEEK.plusDays(7), 9, 10, 60);
    WeekTotals t = calc.compute(List.of(sunday, monday, previousSunday, nextMonday), WEEK);
    assertThat(t.totalMinutes()).isEqualTo(120);
    assertThat(t.minutesPerDay()).containsExactly(60, 0, 0, 0, 0, 0, 60);
  }

  @Test
  @DisplayName("RG-01-13 : le total est la somme des sept jours")
  void totalIsSumOfDays() {
    WeekTotals t =
        calc.compute(
            List.of(
                hour(WEEK, 9, 10, 60),
                day(WEEK.plusDays(2), 420),
                range(WEEK.plusDays(3), WEEK.plusDays(9), 420, 5 * 420)),
            WEEK);
    assertThat(t.totalMinutes())
        .isEqualTo(t.minutesPerDay().stream().mapToInt(Integer::intValue).sum());
    assertThat(t.totalMinutes()).isEqualTo(60 + 420 + 2 * 420);
  }
}
