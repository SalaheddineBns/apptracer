package com.apptracer.referential.service;

import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.repository.ActivityTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityTypeService {

  private final ActivityTypeRepository types;

  public ActivityTypeService(ActivityTypeRepository types) {
    this.types = types;
  }

  /**
   * RG-02-09 : un ADMIN voit actifs et inactifs, les autres seulement les actifs (RG-02-06) ; tri
   * alphabétique par libellé (RG-02-10).
   */
  @Transactional(readOnly = true)
  public List<ActivityType> list(boolean admin) {
    return admin ? types.findAllSorted() : types.findActiveSorted();
  }
}
