package com.gym.checkin.domain;

/** Ghi CA luot duoc phep VA luot bi tu choi — luot bi tu choi la du lieu do hieu qua chong that thoat. */
public enum CheckInResult { ALLOWED, ALLOWED_OVERRIDE, DENIED_EXPIRED, DENIED_FROZEN, DENIED_UNPAID, DENIED_NOT_FOUND, DENIED_SUSPECT, DENIED_ALREADY_INSIDE }
