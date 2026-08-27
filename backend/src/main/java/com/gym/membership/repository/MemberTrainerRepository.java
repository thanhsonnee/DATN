package com.gym.membership.repository;

import com.gym.membership.domain.MemberTrainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberTrainerRepository extends JpaRepository<MemberTrainer, Long> {

    List<MemberTrainer> findByMemberIdAndToDateIsNull(Long memberId);

    List<MemberTrainer> findByTrainerIdAndToDateIsNull(Long trainerId);
}
