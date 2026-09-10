package com.gym.feedback.domain;

/** OPEN -> IN_PROGRESS -> (WAITING_PARTS) -> RESOLVED -> CLOSED. */
public enum FeedbackStatus { OPEN, IN_PROGRESS, WAITING_PARTS, RESOLVED, CLOSED }
