package com.apptracer.referential.service;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import java.util.List;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityTypeService {

  private final ActivityTypeRepository types;
  private final ActivityTypeUsageChecker usageChecker;

  public ActivityTypeService(ActivityTypeRepository types, ActivityTypeUsageChecker usageChecker) {
    this.types = types;
    this.usageChecker = usageChecker;
  }

  /** RG-02-08 : réservé à l'ADMIN (contrôlé par le contrôleur). */
  @Transactional
  public ActivityType create(String code, String label, boolean projectRequired, TagColor color) {
    // RG-02-01 : code unique dans le référentiel (l'index unique couvre les créations concurrentes
    // ;
    // sa violation est traduite en 409 RG-02-01 par le handler global)
    if (types.existsByCode(code)) {
      throw BusinessException.onField(
          "RG-02-01", "error.activity-type.code-duplicate", "code", "field.code-duplicate");
    }
    // RG-02-05 : actif à la création ; RG-00-04 : created_by / updated_by via l'audit JPA
    return types.saveAndFlush(new ActivityType(code, label.strip(), projectRequired, color));
  }

  @Transactional(readOnly = true)
  public ActivityType get(Long id) {
    return types
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.activity-type.not-found"));
  }

  /** RG-02-01 : le code n'est pas modifiable. RG-02-08 : réservé à l'ADMIN. */
  @Transactional
  public ActivityType update(
      Long id, String label, boolean projectRequired, TagColor color, long version) {
    ActivityType type = get(id);
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
    if (type.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(ActivityType.class, id);
    }
    // RG-02-07 : projectRequired n'est plus modifiable dès qu'une saisie utilise le type
    if (type.isProjectRequired() != projectRequired && usageChecker.isUsed(id)) {
      throw BusinessException.onField(
          "RG-02-07",
          "error.activity-type.project-required-locked",
          "projectRequired",
          "field.activity-type.project-required-locked");
    }
    type.setLabel(label.strip());
    type.setProjectRequired(projectRequired); // RG-02-03
    type.setColor(color); // RG-02-04
    // RG-00-04 : updated_at / updated_by via l'audit JPA
    return types.saveAndFlush(type);
  }

  /**
   * RG-02-09 : un ADMIN voit actifs et inactifs, les autres seulement les actifs (RG-02-06) ; tri
   * alphabétique par libellé (RG-02-10).
   */
  /**
   * RG-02-05 : jamais supprimé, seulement désactivé. Sans effet si le type est déjà inactif.
   * RG-02-08 : réservé à l'ADMIN (contrôlé par le contrôleur).
   */
  @Transactional
  public ActivityType deactivate(Long id) {
    return setActive(id, false);
  }

  /** RG-02-05 : réactivable. Sans effet si le type est déjà actif. */
  @Transactional
  public ActivityType reactivate(Long id) {
    return setActive(id, true);
  }

  private ActivityType setActive(Long id, boolean active) {
    ActivityType type = get(id);
    if (type.isActive() == active) {
      return type;
    }
    type.setActive(active);
    // RG-00-04 : updated_at / updated_by via l'audit JPA
    return types.saveAndFlush(type);
  }

  @Transactional(readOnly = true)
  public List<ActivityType> list(boolean admin) {
    return admin ? types.findAllSorted() : types.findActiveSorted();
  }
}
