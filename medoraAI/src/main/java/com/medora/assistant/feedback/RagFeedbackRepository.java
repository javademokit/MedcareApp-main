package com.medora.assistant.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagFeedbackRepository extends JpaRepository<RagFeedbackEntity, Long> {

    @Query("SELECT COUNT(f) FROM RagFeedbackEntity f WHERE f.rating = :rating")
    long countByRating(@Param("rating") String rating);

    @Query("SELECT f.question, COUNT(f) as cnt FROM RagFeedbackEntity f GROUP BY f.question ORDER BY cnt DESC")
    List<Object[]> findTopQuestions();
}
