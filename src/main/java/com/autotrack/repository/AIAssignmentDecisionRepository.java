package com.autotrack.repository;

import com.autotrack.model.AIAssignmentDecision;
import com.autotrack.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AIAssignmentDecisionRepository extends JpaRepository<AIAssignmentDecision, Long> {

    List<AIAssignmentDecision> findByRequestIdOrderByRankPositionAsc(String requestId);

    List<AIAssignmentDecision> findByTaskOrderByCreatedAtDesc(Task task);

    @Query("SELECT d FROM AIAssignmentDecision d " +
           "WHERE d.candidateUser.id = :userId " +
           "  AND d.createdAt >= :since " +
           "ORDER BY d.createdAt DESC")
    List<AIAssignmentDecision> findRecentDecisionsForUser(@Param("userId") Long userId,
                                                          @Param("since") LocalDateTime since);

    @Query("SELECT d FROM AIAssignmentDecision d " +
           "WHERE d.candidateUser.id = :userId AND d.wasSelected = true")
    List<AIAssignmentDecision> findSelectionsForUser(@Param("userId") Long userId);

    long countByRequestId(String requestId);
}
