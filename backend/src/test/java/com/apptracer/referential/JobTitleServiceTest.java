package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
import com.apptracer.referential.service.JobTitleService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class JobTitleServiceTest {

  @Mock private JobTitleRepository titles;
  @InjectMocks private JobTitleService service;

  @Test
  @DisplayName(
      "CA-09-03 / RG-09-05 : refuse un code déjà existant (409 RG-09-05 sur le champ code)")
  void createRejectsDuplicateCode() {
    when(titles.existsByCode("tech_lead")).thenReturn(true);
    assertThatThrownBy(() -> service.create("tech_lead", "X"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-09-05");
              assertThat(e.getErrors()).extracting("field").containsExactly("code");
            });
    verify(titles, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-09-05 : un code libre mais en minuscules est refusé (400 sur le champ code)")
  void createRejectsLowercaseCode() {
    when(titles.existsByCode("ux_designer")).thenReturn(false);
    assertThatThrownBy(() -> service.create("ux_designer", "UX Designer"))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            e -> assertThat(e.getErrors()).extracting("field").containsExactly("code"));
    verify(titles, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-09-05 / RG-09-06 : crée une fonction active, libellé nettoyé")
  void createStoresActiveTitle() {
    when(titles.existsByCode("UX_DESIGNER")).thenReturn(false);
    when(titles.saveAndFlush(any(JobTitle.class))).thenAnswer(i -> i.getArgument(0));
    JobTitle t = service.create("UX_DESIGNER", "  UX Designer ");
    assertThat(t.getCode()).isEqualTo("UX_DESIGNER");
    assertThat(t.getLabel()).isEqualTo("UX Designer");
    assertThat(t.isActive()).isTrue();
  }

  @Test
  @DisplayName("RG-09-05 : le code n'est pas modifié par la mise à jour")
  void updateKeepsCode() {
    JobTitle t = new JobTitle("QA", "QA");
    when(titles.findById(1L)).thenReturn(Optional.of(t));
    when(titles.saveAndFlush(t)).thenReturn(t);
    service.update(1L, " Quality ", 0L);
    assertThat(t.getCode()).isEqualTo("QA");
    assertThat(t.getLabel()).isEqualTo("Quality");
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée est refusée")
  void updateRejectsStaleVersion() {
    when(titles.findById(1L)).thenReturn(Optional.of(new JobTitle("QA", "QA")));
    assertThatThrownBy(() -> service.update(1L, "x", 5L))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("RG-09-06 : désactive puis réactive, sans effet si l'état est déjà celui demandé")
  void deactivateAndReactivate() {
    JobTitle t = new JobTitle("QA", "QA");
    when(titles.findById(1L)).thenReturn(Optional.of(t));
    when(titles.saveAndFlush(t)).thenReturn(t);
    assertThat(service.reactivate(1L).isActive()).isTrue();
    verify(titles, never()).saveAndFlush(any());
    assertThat(service.deactivate(1L).isActive()).isFalse();
    assertThat(service.reactivate(1L).isActive()).isTrue();
  }

  @Test
  @DisplayName("RG-09-07 / RG-09-08 : l'ADMIN lit toutes les fonctions, les autres les actives")
  void listDependsOnRole() {
    List<JobTitle> all = List.of(new JobTitle("A", "A"), new JobTitle("B", "B"));
    List<JobTitle> active = List.of(new JobTitle("A", "A"));
    when(titles.findAllSorted()).thenReturn(all);
    when(titles.findActiveSorted()).thenReturn(active);
    assertThat(service.list(true)).isEqualTo(all);
    assertThat(service.list(false)).isEqualTo(active);
  }

  @Test
  @DisplayName("RG-09-07 : 404 pour une fonction inconnue")
  void getUnknownIsNotFound() {
    when(titles.findById(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
