package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.referential.service.ActivityTypeService;
import com.apptracer.referential.service.ProjectService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferentialServiceTest {

  @Mock private ActivityTypeRepository types;
  @Mock private ProjectRepository projects;
  @Mock private com.apptracer.referential.service.ActivityTypeUsageChecker usageChecker;
  @InjectMocks private ActivityTypeService typeService;
  @InjectMocks private ProjectService projectService;

  private final ActivityType type = new ActivityType("AB", "Ab", false, TagColor.INFO);
  private final Project project = new Project("AB", "Ab");

  @Test
  @DisplayName("RG-02-06 / RG-02-09 : un non-ADMIN ne lit que les types actifs")
  void nonAdminReadsActiveTypes() {
    when(types.findActiveSorted()).thenReturn(List.of(type));
    assertThat(typeService.list(false)).containsExactly(type);
    verify(types).findActiveSorted();
    verifyNoMoreInteractions(types);
  }

  @Test
  @DisplayName("RG-02-09 / RG-02-10 : un ADMIN lit tous les types, triés")
  void adminReadsAllTypes() {
    when(types.findAllSorted()).thenReturn(List.of(type));
    assertThat(typeService.list(true)).containsExactly(type);
    verify(types).findAllSorted();
    verifyNoMoreInteractions(types);
  }

  @Test
  @DisplayName("RG-02-06 / RG-02-09 : un non-ADMIN ne lit que les projets actifs")
  void nonAdminReadsActiveProjects() {
    when(projects.findActiveSorted()).thenReturn(List.of(project));
    assertThat(projectService.list(false)).containsExactly(project);
    verify(projects).findActiveSorted();
    verifyNoMoreInteractions(projects);
  }

  @Test
  @DisplayName("RG-02-09 / RG-02-10 : un ADMIN lit tous les projets, triés")
  void adminReadsAllProjects() {
    when(projects.findAllSorted()).thenReturn(List.of(project));
    assertThat(projectService.list(true)).containsExactly(project);
    verify(projects).findAllSorted();
    verifyNoMoreInteractions(projects);
  }
}
