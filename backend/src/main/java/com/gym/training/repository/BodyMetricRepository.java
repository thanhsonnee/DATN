package com.gym.training.repository;

import com.gym.training.domain.BodyMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BodyMetricRepository extends JpaRepository<BodyMetric, Long> {

    /** Tăng dần theo thời gian — dùng để vẽ biểu đồ tiến bộ cân nặng/BMI. */
    List<BodyMetric> findByMemberIdOrderByMeasuredAtAsc(Long memberId);
}
