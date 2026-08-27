package com.gym.membership.domain;

/** 6 trạng thái. Bỏ DRAFT và EXPIRED_QUOTE vì không còn vai trò báo giá. */
public enum RegistrationStatus { PENDING_PAYMENT, ACTIVE, FROZEN, COMPLETED, CANCELLED, REFUNDED }
