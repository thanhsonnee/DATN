import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemberships } from '@/hooks/useMemberships';
import { useMuaGoi } from '@/hooks/useRegistrations';
import { Card, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Alert';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { Screen } from '@/components/ui/Screen';
import { ApiError } from '@/api/client';
import { tien, tenLoaiGoi } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';
import type { MemberStackParamList } from '@/navigation/types';
import type { Membership } from '@/api/types';

type Props = NativeStackScreenProps<MemberStackParamList, 'BangGia'>;

/**
 * Bảng giá + mua gói. Chỉ tạo hợp đồng ở trạng thái chờ thanh toán — đúng
 * luồng web hiện có, backend chưa có cổng thanh toán online nào. Hội viên vẫn
 * cần thanh toán (tại quầy hoặc chuyển khoản từ xa), lễ tân xác nhận thủ công
 * ở `/quay` thì gói mới kích hoạt.
 */
export function BangGiaScreen({ route }: Props) {
  const renewFromRegistrationId = route.params?.renewFromRegistrationId;
  const { data: goiTap, isLoading, error } = useMemberships();
  const muaGoi = useMuaGoi();

  const [dangChon, setDangChon] = useState<Membership | null>(null);
  const [ketQua, setKetQua] = useState<string | null>(null);

  if (isLoading) return <Spinner label="Đang tải bảng giá…" />;
  if (error) {
    return (
      <Screen scroll={false} style={styles.center}>
        <Alert tone="error">Không tải được bảng giá. Kiểm tra kết nối.</Alert>
      </Screen>
    );
  }

  const xacNhanMua = () => {
    if (!dangChon) return;
    muaGoi.mutate(
      { membershipId: dangChon.id, renewFromRegistrationId },
      {
        onSuccess: (hopDong) => {
          setDangChon(null);
          setKetQua(`Đã tạo hợp đồng ${hopDong.registrationCode}. Vui lòng thanh toán để kích hoạt gói tập.`);
        },
      },
    );
  };

  return (
    <Screen>
      {renewFromRegistrationId != null && (
        <Alert tone="info">
          Đang gia hạn từ hợp đồng #{renewFromRegistrationId} — gói mới sẽ tự động bắt đầu ngay
          sau khi hợp đồng hiện tại kết thúc, không mất ngày còn lại.
        </Alert>
      )}

      {ketQua && <Alert tone="success">{ketQua}</Alert>}

      {goiTap?.map((goi) => (
        <Card key={goi.id}>
          <CardBody>
            <View style={styles.headRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.name}>{goi.name}</Text>
                <Text style={styles.code}>{goi.code}</Text>
              </View>
              {goi.includesTrainer && <Badge tone="blue">Có PT</Badge>}
            </View>

            <Text style={styles.price}>{tien(goi.price)}</Text>

            <View style={styles.grid}>
              <O nhan="Loại gói" giaTri={tenLoaiGoi(goi.packageType)} />
              {goi.durationDays != null && <O nhan="Thời hạn" giaTri={`${goi.durationDays} ngày`} />}
              {goi.sessionCount != null && <O nhan="Số buổi PT" giaTri={`${goi.sessionCount} buổi`} />}
              <O nhan="Bảo lưu" giaTri={goi.maxFreezeDays > 0 ? `Tối đa ${goi.maxFreezeDays} ngày` : 'Không áp dụng'} />
            </View>

            <Button onPress={() => setDangChon(goi)}>Mua gói này</Button>
          </CardBody>
        </Card>
      ))}

      <Modal open={!!dangChon} title="Xác nhận mua gói" onClose={() => setDangChon(null)}>
        {dangChon && (
          <>
            <View style={styles.confirmBox}>
              <Text style={styles.confirmName}>{dangChon.name}</Text>
              <Text style={styles.confirmPrice}>{tien(dangChon.price)}</Text>
            </View>

            <Text style={styles.hint}>
              Hợp đồng sẽ được tạo ở trạng thái chờ thanh toán. Sau khi thanh toán, lễ tân xác
              nhận thì gói mới bắt đầu có hiệu lực.
            </Text>

            {muaGoi.error instanceof ApiError && <Alert tone="error">{muaGoi.error.message}</Alert>}

            <View style={styles.rowEnd}>
              <Button variant="secondary" onPress={() => setDangChon(null)}>Hủy</Button>
              <Button onPress={xacNhanMua} loading={muaGoi.isPending}>Xác nhận mua</Button>
            </View>
          </>
        )}
      </Modal>
    </Screen>
  );
}

function O({ nhan, giaTri }: { nhan: string; giaTri: string }) {
  return (
    <View style={{ width: '45%' }}>
      <Text style={styles.oLabel}>{nhan}</Text>
      <Text style={styles.oValue}>{giaTri}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center' },
  headRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  name: { fontSize: 15, fontWeight: '700', color: colors.slate900 },
  code: { fontSize: 11, color: colors.slate400, marginTop: 2 },
  price: { fontSize: 20, fontWeight: '800', color: colors.brand700 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  oLabel: { fontSize: 11.5, color: colors.slate500 },
  oValue: { fontSize: 13.5, fontWeight: '600', color: colors.slate800, marginTop: 2 },
  confirmBox: { backgroundColor: colors.slate50, borderRadius: 10, padding: spacing.md, gap: 4 },
  confirmName: { fontSize: 14, fontWeight: '600', color: colors.slate900 },
  confirmPrice: { fontSize: 18, fontWeight: '800', color: colors.brand700 },
  hint: { fontSize: 13, color: colors.slate600 },
  rowEnd: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing.sm },
});
