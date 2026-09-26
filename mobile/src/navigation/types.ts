export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
};

export type MemberTabParamList = {
  GoiCuaToi: undefined;
  CheckIn: undefined;
  BuoiTap: undefined;
  PhanHoi: undefined;
  TaiKhoan: undefined;
};

/**
 * Bọc ngoài MemberTabs — "Bảng giá" cố tình KHÔNG phải 1 tab cố định (mua gói
 * là thao tác hiếm, không cần chiếm chỗ thanh tab dưới cùng như check-in/buổi
 * tập), mà là màn hình được "push" từ nút trong tab Gói của tôi.
 */
export type MemberStackParamList = {
  Tabs: undefined;
  BangGia: { renewFromRegistrationId?: number } | undefined;
};

export type TrainerTabParamList = {
  LichDay: undefined;
  DanhGia: undefined;
  TaiKhoan: undefined;
};
