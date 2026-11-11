package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TeamPersistenceTest {

  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User manager;
  private User alice;
  private User bob;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tp-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    manager = users.save(new User("tp-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
    alice = users.save(new User("tp-alice@example.com", "Alice", "Aa", "hash", Role.USER));
    bob = users.save(new User("tp-bob@example.com", "Bob", "Bb", "hash", Role.USER));
    // RG-00-04: the author of the writes is the signed-in ADMIN
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tp-%'");
  }

  @Test
  @DisplayName("RG-05-14 / RG-00-04 : une équipe enregistrée porte created_by et updated_by")
  void teamIsAudited() {
    Team team = teams.saveAndFlush(new Team("Produit", manager));

    Team loaded = teams.findActiveById(team.getId()).orElseThrow();
    assertThat(loaded.getName()).isEqualTo("Produit");
    assertThat(loaded.getManager().getId()).isEqualTo(manager.getId());
    assertThat(loaded.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(loaded.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("RG-05-01 : deux équipes ne peuvent pas porter le même nom, casse ignorée")
  void nameIsUniqueIgnoringCase() {
    teams.saveAndFlush(new Team("Produit", manager));

    assertThatThrownBy(() -> teams.saveAndFlush(new Team("PRODUIT", manager)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ux_teams_name_lower");
  }

  @Test
  @DisplayName("RG-05-01 : un nom de moins de 2 caractères est refusé par la base")
  void nameMinLengthCheck() {
    assertThatThrownBy(() -> teams.saveAndFlush(new Team("A", manager)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_teams_name_length");
  }

  @Test
  @DisplayName("RG-05-09 / RG-05-14 : le nom d'une équipe supprimée redevient disponible")
  void deletedTeamFreesItsName() {
    Team team = teams.saveAndFlush(new Team("Produit", manager));
    team.markDeleted(admin.getId(), Instant.now());
    teams.saveAndFlush(team);

    assertThat(teams.findActiveById(team.getId())).isEmpty();
    Team again = teams.saveAndFlush(new Team("produit", manager));
    assertThat(again.getId()).isNotEqualTo(team.getId());
    // the deleted row is kept, with who and when
    Team kept = teams.findById(team.getId()).orElseThrow();
    assertThat(kept.getDeletedBy()).isEqualTo(admin.getId());
    assertThat(kept.getDeletedAt()).isNotNull();
  }

  @Test
  @DisplayName("RG-05-14 : deleted_at et deleted_by vont ensemble")
  void deletionColumnsGoTogether() {
    Team team = teams.saveAndFlush(new Team("Produit", manager));

    assertThatThrownBy(
            () -> jdbc.update("UPDATE teams SET deleted_at = now() WHERE id = ?", team.getId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_teams_deletion");
  }

  @Test
  @DisplayName("RG-05-06 : un même manager peut l'être de plusieurs équipes")
  void managerOfSeveralTeams() {
    teams.saveAndFlush(new Team("Produit", manager));
    teams.saveAndFlush(new Team("Technique", manager));

    assertThat(teams.findActiveNamesByManagerId(manager.getId()))
        .containsExactly("Produit", "Technique");
  }

  @Test
  @DisplayName("RG-05-07 : une équipe sans membre est valide")
  void teamWithoutMembers() {
    Team team = teams.saveAndFlush(new Team("Vide", manager));

    assertThat(members.findByTeamIdsSorted(java.util.List.of(team.getId()))).isEmpty();
  }

  @Test
  @DisplayName("RG-05-04 : un compte ne peut être rattaché qu'à une seule équipe")
  void accountBelongsToAtMostOneTeam() {
    Team a = teams.saveAndFlush(new Team("A-team", manager));
    Team b = teams.saveAndFlush(new Team("B-team", manager));
    members.saveAndFlush(new TeamMember(alice.getId(), a.getId()));

    assertThatThrownBy(() -> members.saveAndFlush(new TeamMember(alice.getId(), b.getId())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("pk_team_members");
  }

  @Test
  @DisplayName("RG-05-14 : un rattachement porte qui l'a créé et quand ; membres triés par nom")
  void memberIsAuditedAndSorted() {
    Team team = teams.saveAndFlush(new Team("Produit", manager));
    members.saveAndFlush(new TeamMember(bob.getId(), team.getId()));
    members.saveAndFlush(new TeamMember(alice.getId(), team.getId()));

    var found = members.findByTeamIdsSorted(java.util.List.of(team.getId()));
    assertThat(found).extracting(TeamMember::getUserId).containsExactly(alice.getId(), bob.getId());
    assertThat(found.get(0).getCreatedBy()).isEqualTo(admin.getId());
    assertThat(found.get(0).getCreatedAt()).isNotNull();
    assertThat(members.findAllWithTeam()).hasSize(2);
  }
}
