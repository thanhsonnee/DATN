package com.gym.checkin.domain;

/**
 * Ghi CA luot duoc phep VA luot bi tu choi — luot bi tu choi la du lieu do hieu qua chong
 * that thoat.
 *
 * <p>{@code DENIED_NOT_FOUND} hien khong bao gio duoc gan: khi khong tim thay hoi vien,
 * {@link com.gym.checkin.service.CheckInService#quetVao} nem loi 404 truoc khi tao duoc
 * dong CheckIn nao (member_id la khoa ngoai bat buoc, khong the luu voi id khong ton tai).
 * Giu lai gia tri nay (thay vi xoa) vi no da nam trong CHECK constraint cua bang check_ins
 * va nhan hien thi o frontend — chi xoa neu co ly do sua schema khac di kem.
 *
 * <p>{@code DENIED_MANUAL}: le tan tu choi thu cong mot yeu cau tu check-in tu hang doi
 * (khong phai quet vao that), luon kem ly do bat buoc trong {@code incidentNote}. Xem
 * {@link com.gym.checkin.service.CheckInService#tuChoiYeuCauTuCheckIn}.
 */
public enum CheckInResult { ALLOWED, ALLOWED_OVERRIDE, DENIED_EXPIRED, DENIED_FROZEN, DENIED_UNPAID, DENIED_NOT_FOUND, DENIED_SUSPECT, DENIED_ALREADY_INSIDE, DENIED_MANUAL }
