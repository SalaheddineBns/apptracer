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

  /** RG-09-07: ADMIN only (checked by the controller). */
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

  /** RG-09-05: the code cannot be changed. RG-09-07: ADMIN only. */
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
   * RG-09-06: never deleted, only deactivated; the accounts that hold it keep it. No effect if the
   * job title is already inactive. RG-09-07: ADMIN only.
   */
  @Transactional
  public JobTitle deactivate(Long id) {
    return setActive(id, false);
  }

  /** RG-09-06: can be reactivated. No effect if the job title is already active. */
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
   * RG-09-07: an ADMIN sees active and inactive ones, the others only the active ones; RG-09-08:
   * sorted alphabetically by label.
   */
  @Transactional(readOnly = true)
  public List<JobTitle> list(boolean admin) {
    return admin ? titles.findAllSorted() : titles.findActiveSorted();
  }
}
