package com.apptracer.account.api;

import com.apptracer.account.domain.User;
import com.apptracer.referential.domain.JobTitle;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRole(),
        user.isActive(),
        toResponse(user.getJobTitle()),
        user.getVersion());
  }

  private UserJobTitleResponse toResponse(JobTitle title) {
    return title == null
        ? null
        : new UserJobTitleResponse(title.getCode(), title.getLabel(), title.isActive());
  }

  public PageUserResponse toResponse(Page<User> page) {
    return new PageUserResponse(
        page.getContent().stream().map(this::toResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }
}
