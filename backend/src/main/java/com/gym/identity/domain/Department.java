package com.gym.identity.domain;

/**
 * 5 phòng ban: 4 cái đầu khớp đúng 4 vai trò nhân viên thường (TRAINER/SALE/
 * RECEPTIONIST/ACCOUNTANT), MANAGEMENT dành riêng cho Admin — Admin không
 * phải huấn luyện viên nên không được gộp vào TRAINING.
 */
public enum Department { TRAINING, SALES, FRONT_DESK, ACCOUNTING, MANAGEMENT }
