package com.gym.training.repository;

import com.gym.training.domain.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    List<WorkoutPlan> findByRegistrationMemberIdOrderByCreatedAtDesc(Long memberId);
}
