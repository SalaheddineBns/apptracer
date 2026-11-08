package com.apptracer.referential.service;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobTitleService {

  private final JobTitleRepository titles;

  public JobTitleService(JobTitleRepository titles) {
    this.titles = titles;
  }

  /** RG-09-07 : réservé à l'ADMIN (contrôlé par le contrôleur). */
  @Transactional
  public JobTitle create(String code, String label) {
    // RG-09-05 : code unique sans tenir compte de la casse (l'index unique couvre les créations
    // concurrentes ; sa violation est traduite en 409 RG-09-05 par le handler global)
    if (titles.existsByCode(code)) {
      throw BusinessException.onField(
          "RG-09-05", "error.job-title.code-duplicate", "code", "field.code-duplicate");
    }
    // RG-09-05 : le code est en majuscules
    if (!code.equals(code.toUpperCase(Locale.ROOT))) {
      throw new InvalidRequestException(
          "error.job-title.code-format", "code", "validation.code.pattern");
    }
    // RG-09-06 : active à la création ; RG-09-11 : created_by / updated_by via l'audit JPA
    return titles.saveAndFlush(new JobTitle(code, label.strip()));
  }

  @Transactional(readOnly = true)
  public JobTitle get(Long id) {
    return titles
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.job-title.not-found"));
  }

  /** RG-09-05 : le code n'est pas modifiable. RG-09-07 : réservé à l'ADMIN. */
  @Transactional
  public JobTitle update(Long id, String label, long version) {
    JobTitle title = get(id);
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
    if (title.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(JobTitle.class, id);
    }
    title.setLabel(label.strip());
    // RG-09-11 : updated_at / updated_by via l'audit JPA
    return titles.saveAndFlush(title);
  }

  /**
   * RG-09-06 : jamais supprimée, seulement désactivée ; les comptes qui la portent la gardent. Sans
   * effet si la fonction est déjà inactive. RG-09-07 : réservé à l'ADMIN.
   */
  @Transactional
  public JobTitle deactivate(Long id) {
    return setActive(id, false);
  }

  /** RG-09-06 : réactivable. Sans effet si la fonction est déjà active. */
  @Transactional
  public JobTitle reactivate(Long id) {
    return setActive(id, true);
  }

  private JobTitle setActive(Long id, boolean active) {
    JobTitle title = get(id);
    if (title.isActive() == active) {
      return title;
    }
    title.setActive(active);
    // RG-09-11 : updated_at / updated_by via l'audit JPA
    return titles.saveAndFlush(title);
  }

  /**
   * RG-09-07 : un ADMIN voit actives et inactives, les autres seulement les actives ; RG-09-08 :
   * tri alphabétique par libellé.
   */
  @Transactional(readOnly = true)
  public List<JobTitle> list(boolean admin) {
    return admin ? titles.findAllSorted() : titles.findActiveSorted();
  }
}
