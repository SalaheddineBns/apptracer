package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.service.ActivityTypeService;
import com.apptracer.referential.service.ActivityTypeUsageChecker;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ActivityTypeServiceTest {

  @Mock private ActivityTypeRepository types;
  @Mock private ActivityTypeUsageChecker usageChecker;
  @InjectMocks private ActivityTypeService service;

  private ActivityType existing() {
    return new ActivityType("REUNION", "Réunion", false, TagColor.WARN);
  }

  @Test
  @DisplayName("RG-02-01 : refuse un code déjà existant (409 RG-02-01 sur le champ code)")
  void createRejectsDuplicateCode() {
    when(types.existsByCode("REUNION")).thenReturn(true);
    assertThatThrownBy(() -> service.create("REUNION", "X", false, TagColor.INFO))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-02-01");
              assertThat(e.getErrors()).extracting("field").containsExactly("code");
            });
    verify(types, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-02-03 / RG-02-04 / RG-02-05 : crée un type actif avec ses attributs")
  void createStoresType() {
    when(types.existsByCode("NOUVEAU")).thenReturn(false);
    when(types.saveAndFlush(any(ActivityType.class))).thenAnswer(i -> i.getArgument(0));
    ActivityType t = service.create("NOUVEAU", "  Nouveau ", true, TagColor.SUCCESS);
    assertThat(t.getCode()).isEqualTo("NOUVEAU");
    assertThat(t.getLabel()).isEqualTo("Nouveau");
    assertThat(t.isProjectRequired()).isTrue();
    assertThat(t.getColor()).isEqualTo(TagColor.SUCCESS);
    assertThat(t.isActive()).isTrue();
  }

  @Test
  @DisplayName("RG-02-01 : le code n'est pas modifié par la mise à jour")
  void updateKeepsCode() {
    ActivityType t = existing();
    when(types.findById(1L)).thenReturn(Optional.of(t));
    when(types.saveAndFlush(t)).thenReturn(t);
    service.update(1L, "Autre libellé", false, TagColor.INFO, 0L);
    assertThat(t.getCode()).isEqualTo("REUNION");
    assertThat(t.getLabel()).isEqualTo("Autre libellé");
    assertThat(t.getColor()).isEqualTo(TagColor.INFO);
  }

  @Test
  @DisplayName(
      "CA-02-10 / RG-02-07 : refuse (409 RG-02-07) de changer projectRequired si une saisie utilise le type")
  void updateRejectsProjectRequiredChangeWhenUsed() {
    ActivityType t = existing();
    when(types.findById(1L)).thenReturn(Optional.of(t));
    when(usageChecker.isUsed(1L)).thenReturn(true);
    assertThatThrownBy(() -> service.update(1L, "Réunion", true, TagColor.WARN, 0L))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-02-07"));
    assertThat(t.isProjectRequired()).isFalse();
    verify(types, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-02-07 : les autres champs restent modifiables même si le type est utilisé")
  void updateOtherFieldsAllowedWhenUsed() {
    ActivityType t = existing();
    when(types.findById(1L)).thenReturn(Optional.of(t));
    when(types.saveAndFlush(t)).thenReturn(t);
    service.update(1L, "Nouveau libellé", false, TagColor.SUCCESS, 0L);
    assertThat(t.getLabel()).isEqualTo("Nouveau libellé");
    assertThat(t.getColor()).isEqualTo(TagColor.SUCCESS);
  }

  @Test
  @DisplayName("RG-02-07 : projectRequired modifiable tant qu'aucune saisie n'utilise le type")
  void updateProjectRequiredAllowedWhenUnused() {
    ActivityType t = existing();
    when(types.findById(1L)).thenReturn(Optional.of(t));
    when(usageChecker.isUsed(1L)).thenReturn(false);
    when(types.saveAndFlush(t)).thenReturn(t);
    service.update(1L, "Réunion", true, TagColor.WARN, 0L);
    assertThat(t.isProjectRequired()).isTrue();
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée est refusée")
  void updateRejectsStaleVersion() {
    when(types.findById(1L)).thenReturn(Optional.of(existing()));
    assertThatThrownBy(() -> service.update(1L, "X", false, TagColor.INFO, 5L))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("RG-02-05 : désactive puis réactive un type, sans jamais le supprimer")
  void deactivateThenReactivate() {
    ActivityType t = existing();
    when(types.findById(1L)).thenReturn(Optional.of(t));
    when(types.saveAndFlush(t)).thenReturn(t);
    assertThat(service.deactivate(1L).isActive()).isFalse();
    assertThat(service.reactivate(1L).isActive()).isTrue();
    verify(types, never()).delete(any());
    verify(types, never()).deleteById(any());
  }

  @Test
  @DisplayName("RG-02-05 : désactiver un type déjà inactif, ou réactiver un actif, est sans effet")
  void redundantToggleIsNoOp() {
    ActivityType t = existing();
    when(types.findById(1L)).thenReturn(Optional.of(t));
    assertThat(service.reactivate(1L).isActive()).isTrue();
    t.setActive(false);
    assertThat(service.deactivate(1L).isActive()).isFalse();
    verify(types, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Un identifiant inconnu donne une ressource introuvable")
  void getUnknown() {
    when(types.findById(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
