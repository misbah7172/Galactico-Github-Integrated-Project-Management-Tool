package com.autotrack.repository;

import com.autotrack.model.ContributionScore;
import com.autotrack.model.Project;
import com.autotrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContributionScoreRepository extends JpaRepository<ContributionScore, Long> {

    /** Latest snapshot for a user on a project. */
    Optional<ContributorScoreProjection> findFirstByProjectAndUserOrderByScoredAtDesc(Project project, User user);

    /** Latest snapshot per user for a project (for ranking). */
    @Query("SELECT cs FROM ContributionScore cs " +
           "WHERE cs.project = :project " +
           "  AND cs.scoredAt = (SELECT MAX(x.scoredAt) FROM ContributionScore x " +
           "                     WHERE x.project = cs.project AND x.user = cs.user) " +
           "ORDER BY cs.netScore DESC")
    List<ContributionScore> findLatestScoresByProject(@Param("project") Project project);

    /** History for one user/project — used for trend charts. */
    List<ContributionScore> findByProjectAndUserOrderByScoredAtDesc(Project project, User user);

    /** All snapshots in a window — used for project health dashboards. */
    @Query("SELECT cs FROM ContributionScore cs " +
           "WHERE cs.project = :project AND cs.scoredAt >= :since " +
           "ORDER BY cs.scoredAt DESC")
    List<ContributionScore> findRecentScoresByProject(@Param("project") Project project,
                                                      @Param("since") LocalDateTime since);

    /** Average net score across the latest snapshot of every member on a project. */
    @Query("SELECT AVG(cs.netScore) FROM ContributionScore cs " +
           "WHERE cs.project = :project " +
           "  AND cs.scoredAt = (SELECT MAX(x.scoredAt) FROM ContributionScore x " +
           "                     WHERE x.project = cs.project AND x.user = cs.user)")
    Double averageNetScoreByProject(@Param("project") Project project);

    interface ContributorScoreProjection {
        Long getId();
        Double getNetScore();
        LocalDateTime getScoredAt();
    }
}
