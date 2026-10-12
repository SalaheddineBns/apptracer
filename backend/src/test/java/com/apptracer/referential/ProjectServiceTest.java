package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.referential.service.ProjectService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock private ProjectRepository projects;
  @InjectMocks private ProjectService service;

  @Test
  @DisplayName("RG-02-01 : refuse un code déjà existant (409 RG-02-01 sur le champ code)")
  void createRejectsDuplicateCode() {
    when(projects.existsByCode("P1")).thenReturn(true);
    assertThatThrownBy(() -> service.create("P1", "Projet"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-02-01");
              assertThat(e.getErrors()).extracting("field").containsExactly("code");
            });
    verify(projects, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-02-05 : crée un projet actif")
  void createStoresActiveProject() {
    when(projects.existsByCode("P1")).thenReturn(false);
    when(projects.saveAndFlush(any(Project.class))).thenAnswer(i -> i.getArgument(0));
    Project p = service.create("P1", "  Projet un ");
    assertThat(p.getCode()).isEqualTo("P1");
    assertThat(p.getName()).isEqualTo("Projet un");
    assertThat(p.isActive()).isTrue();
  }

  @Test
  @DisplayName("RG-02-01 : le code n'est pas modifié par la mise à jour")
  void updateKeepsCode() {
    Project p = new Project("P1", "Ancien");
    when(projects.findById(1L)).thenReturn(Optional.of(p));
    when(projects.saveAndFlush(p)).thenReturn(p);
    service.update(1L, "Nouveau", 0L);
    assertThat(p.getCode()).isEqualTo("P1");
    assertThat(p.getName()).isEqualTo("Nouveau");
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée est refusée")
  void updateRejectsStaleVersion() {
    when(projects.findById(1L)).thenReturn(Optional.of(new Project("P1", "N")));
    assertThatThrownBy(() -> service.update(1L, "X", 5L))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("RG-02-05 : désactive puis réactive un projet, sans jamais le supprimer")
  void deactivateThenReactivate() {
    Project p = new Project("P1", "N");
    when(projects.findById(1L)).thenReturn(Optional.of(p));
    when(projects.saveAndFlush(p)).thenReturn(p);
    assertThat(service.deactivate(1L).isActive()).isFalse();
    assertThat(service.reactivate(1L).isActive()).isTrue();
    verify(projects, never()).delete(any());
    verify(projects, never()).deleteById(any());
  }

  @Test
  @DisplayName("RG-02-05 : désactivation ou réactivation redondante sans effet")
  void redundantToggleIsNoOp() {
    Project p = new Project("P1", "N");
    when(projects.findById(1L)).thenReturn(Optional.of(p));
    assertThat(service.reactivate(1L).isActive()).isTrue();
    p.setActive(false);
    assertThat(service.deactivate(1L).isActive()).isFalse();
    verify(projects, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Un identifiant inconnu donne une ressource introuvable")
  void getUnknown() {
    when(projects.findById(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
