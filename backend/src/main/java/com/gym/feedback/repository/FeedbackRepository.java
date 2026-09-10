package com.gym.feedback.repository;

import com.gym.feedback.domain.Feedback;
import com.gym.feedback.domain.FeedbackStatus;
import com.gym.feedback.domain.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    List<Feedback> findByFeedbackTypeOrderByCreatedAtDesc(FeedbackType type);

    List<Feedback> findByStatusAndFeedbackTypeOrderByCreatedAtDesc(FeedbackStatus status, FeedbackType type);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    List<Feedback> findByTrainerIdAndRatingIsNotNull(Long trainerId);

    List<Feedback> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);

    /** Phản hồi còn cần xử lý (chưa RESOLVED/CLOSED) — dùng cho dashboard Admin. */
    long countByStatusIn(List<FeedbackStatus> statuses);

    /** Phản hồi khẩn cấp còn mở (vd. đánh giá PT thấp điểm) — dùng cho dashboard Admin. */
    long countByUrgentTrueAndStatusIn(List<FeedbackStatus> statuses);
}
