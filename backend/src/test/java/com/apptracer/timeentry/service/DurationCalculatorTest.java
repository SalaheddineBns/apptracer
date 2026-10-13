package com.apptracer.timeentry.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apptracer.timeentry.domain.Granularity;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DurationCalculatorTest {

  private final DurationCalculator calc = new DurationCalculator();

  private int hour(LocalTime from, LocalTime to) {
    return calc.compute(Granularity.HOUR, LocalDate.of(2026, 10, 5), null, from, to, 420);
  }

  private int day(LocalDate d, int std) {
    return calc.compute(Granularity.DAY, d, null, null, null, std);
  }

  private int range(LocalDate from, LocalDate to, int std) {
    return calc.compute(Granularity.RANGE, from, to, null, null, std);
  }

  @Test
  @DisplayName("CA-01-01 : 09:00-10:30 vaut 90 min")
  void hourNinetyMinutes() {
    assertThat(hour(LocalTime.of(9, 0), LocalTime.of(10, 30))).isEqualTo(90); // RG-01-08
  }

  @Test
  @DisplayName("RG-01-08 : HEURE, valeurs limites 15 min et 23:45 - 00:00 exclu")
  void hourBounds() {
    assertThat(hour(LocalTime.of(9, 0), LocalTime.of(9, 15))).isEqualTo(15);
    assertThat(hour(LocalTime.of(0, 0), LocalTime.of(23, 45))).isEqualTo(1425);
  }

  @Test
  @DisplayName("RG-01-08 : HEURE ne dépend pas de la journée standard")
  void hourIgnoresStandardDay() {
    assertThat(
            calc.compute(
                Granularity.HOUR,
                LocalDate.of(2026, 10, 5),
                null,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                1440))
        .isEqualTo(60);
  }

  @Test
  @DisplayName("RG-01-08 : JOURNEE vaut une journée standard, un jour de semaine")
  void dayWeekday() {
    assertThat(day(LocalDate.of(2026, 10, 5), 420)).isEqualTo(420);
    assertThat(day(LocalDate.of(2026, 10, 5), 480)).isEqualTo(480);
  }

  @Test
  @DisplayName("RG-01-08 : JOURNEE un samedi ou un dimanche vaut une journée standard")
  void dayWeekend() {
    assertThat(day(LocalDate.of(2026, 10, 10), 420)).isEqualTo(420); // samedi
    assertThat(day(LocalDate.of(2026, 10, 11), 420)).isEqualTo(420); // dimanche
  }

  @Test
  @DisplayName("CA-01-03 : PLAGE du lundi 05/10 au vendredi 16/10/2026 = 10 jours ouvrés, 4200 min")
  void rangeTwoWeeks() {
    assertThat(range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 16), 420)).isEqualTo(4200);
  }

  @Test
  @DisplayName("RG-01-08 : PLAGE, week-ends exclus du décompte")
  void rangeSkipsWeekends() {
    // vendredi 09/10 -> lundi 12/10 : 2 jours ouvrés
    assertThat(range(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12), 420)).isEqualTo(840);
    // lundi -> dimanche : 5 jours ouvrés
    assertThat(range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), 420)).isEqualTo(2100);
  }

  @Test
  @DisplayName("RG-01-08 : PLAGE samedi-dimanche sans jour ouvré vaut 0 min")
  void rangeWithoutWeekday() {
    assertThat(range(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), 420)).isZero();
  }

  @Test
  @DisplayName("RG-01-08 : PLAGE, bornes de 2 jours et de 31 jours")
  void rangeBounds() {
    // 2 jours ouvrés consécutifs
    assertThat(range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), 420)).isEqualTo(840);
    // 31 jours du lundi 05/10 au mercredi 04/11/2026 : 23 jours ouvrés
    assertThat(range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 4), 420))
        .isEqualTo(23 * 420);
    // 31 jours du samedi 03/10 au lundi 02/11/2026 : 21 jours ouvrés
    assertThat(range(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 2), 420))
        .isEqualTo(21 * 420);
  }

  @Test
  @DisplayName("RG-01-08 : PLAGE utilise la journée standard reçue en paramètre")
  void rangeUsesGivenStandardDay() {
    assertThat(range(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), 360)).isEqualTo(1800);
  }
}
