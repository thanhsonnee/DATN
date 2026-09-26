import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMyRegistrations, useXinBaoLuu } from '@/hooks/useRegistrations';
import { useRefetchOnFocus } from '@/hooks/useRefetchOnFocus';
import { useAuth } from '@/stores/auth';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge, tonesForRegistration } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/ui/Alert';
import { Modal } from '@/components/ui/Modal';
import { EmptyState, Spinner } from '@/components/ui/Spinner';
import { Screen } from '@/components/ui/Screen';
import { ApiError } from '@/api/client';
import { ngay, soNgayConLai, tien, tenLoaiGoi, tenTrangThaiBaoLuu, tenTrangThaiHopDong, truNgay } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';
import type { MemberStackParamList } from '@/navigation/types';
import type { Registration } from '@/api/types';

export function GoiCuaToiScreen() {
  const user = useAuth((s) => s.user);
  const refreshUser = useAuth((s) => s.refreshUser);
  const navigation = useNavigation<NativeStackNavigationProp<MemberStackParamList>>();
  const { data: hopDongs, isLoading, refetch, isRefetching } = useMyRegistrations();
  const [xinBaoLuuCho, setXinBaoLuuCho] = useState<Registration | null>(null);

  // Lễ tân kích hoạt hợp đồng / duyệt bảo lưu ở web, hoặc hội viên vừa mua gói
  // lần đầu — quay lại tab này phải thấy đúng trạng thái mới, không cần kéo
  // làm mới tay.
  useRefetchOnFocus(() => { refetch(); refreshUser(); });

  if (isLoading) return <Spinner label="Đang tải gói tập…" />;

  return (
    <Screen onRefresh={refetch} refreshing={isRefetching}>
      <View style={styles.headRow}>
        <Text style={styles.h1}>Gói tập của tôi</Text>
        <Button onPress={() => navigation.navigate('BangGia', undefined)}>+ Mua gói mới</Button>
      </View>

      {!user?.isMember && (
        <Alert tone="info">Bạn chưa mua gói tập nào — bấm "+ Mua gói mới" để xem bảng giá.</Alert>
      )}

      {hopDongs?.length === 0 && user?.isMember && <EmptyState title="Chưa có hợp đồng nào" />}

      {hopDongs?.map((hd) => (
        <TheHopDong
          key={hd.id} hopDong={hd} onXinBaoLuu={() => setXinBaoLuuCho(hd)}
          onGiaHan={() => navigation.navigate('BangGia', { renewFromRegistrationId: hd.id })}
        />
      ))}

      <HopThoaiBaoLuu hopDong={xinBaoLuuCho} onClose={() => setXinBaoLuuCho(null)} />
    </Screen>
  );
}

function TheHopDong({ hopDong, onXinBaoLuu, onGiaHan }: {
  hopDong: Registration; onXinBaoLuu: () => void; onGiaHan: () => void;
}) {
  const conLai = soNgayConLai(hopDong.endDate);
  const dangChay = hopDong.status === 'ACTIVE';
  const chiTrongMotNgay = hopDong.startDate === hopDong.endDate;
  const chuaTungBaoLuu = hopDong.freeze === null;
  const goiChoBaoLuu = hopDong.maxFreezeDays > 0;
  // Cùng điều kiện với web: hợp đồng đã xong, hoặc đang chạy nhưng sắp hết hạn (≤14 ngày)
  const choGiaHan = hopDong.status === 'COMPLETED' || (dangChay && conLai !== null && conLai <= 14);

  return (
    <Card>
      <CardHeader
        title={hopDong.membershipName}
        subtitle={hopDong.registrationCode}
        action={<Badge tone={tonesForRegistration(hopDong.status)}>{tenTrangThaiHopDong(hopDong.status)}</Badge>}
      />
      <CardBody>
        <View style={styles.grid}>
          <O nhan="Loại gói" giaTri={tenLoaiGoi(hopDong.packageType)} />
          <O nhan="Ngày ký" giaTri={ngay(hopDong.contractDate)} />
          {chiTrongMotNgay ? (
            <O nhan="Có hiệu lực" giaTri={`Trong ngày ${ngay(hopDong.startDate)}`} />
          ) : (
            <>
              <O nhan="Hiệu lực từ" giaTri={ngay(hopDong.startDate)} />
              <O nhan="Hết hạn" giaTri={ngay(hopDong.endDate)} />
            </>
          )}
          {hopDong.sessionsTotal != null && <O nhan="Số buổi PT" giaTri={`${hopDong.sessionsTotal} buổi`} />}
          <O nhan="Thành tiền" giaTri={tien(hopDong.finalPrice)} noiBat />
        </View>

        {dangChay && conLai !== null && (
          <Alert tone={conLai <= 14 ? 'error' : 'info'}>
            {conLai < 0 ? 'Gói đã hết hạn'
              : conLai === 0 ? 'Hết hạn hôm nay'
              : `Còn ${conLai} ngày sử dụng${conLai <= 14 ? ' — nên gia hạn sớm' : ''}`}
          </Alert>
        )}

        {hopDong.status === 'PENDING_PAYMENT' && (
          <Alert tone="info">Vui lòng thanh toán tại quầy. Lễ tân kích hoạt xong thì gói mới bắt đầu tính ngày.</Alert>
        )}

        {hopDong.freeze && (
          <View style={styles.freezeBox}>
            <Text style={styles.freezeTitle}>
              Bảo lưu: {ngay(hopDong.freeze.fromDate)} → {ngay(hopDong.freeze.toDate)}
              {hopDong.freeze.days ? ` (${hopDong.freeze.days} ngày)` : ''}
            </Text>
            <Text style={styles.freezeText}>
              Trạng thái: {tenTrangThaiBaoLuu(hopDong.freeze.status)} · {hopDong.freeze.reason}
            </Text>
            {!['PENDING', 'REJECTED'].includes(hopDong.freeze.status) && hopDong.endDate && hopDong.freeze.days && (
              <Text style={styles.freezeTitle}>
                Hạn cũ: {ngay(truNgay(hopDong.endDate, hopDong.freeze.days))} → Hạn mới: {ngay(hopDong.endDate)}
              </Text>
            )}
          </View>
        )}

        <View style={styles.rowEnd}>
          {choGiaHan && <Button onPress={onGiaHan}>Gia hạn gói tập</Button>}
          {dangChay && chuaTungBaoLuu && goiChoBaoLuu && (
            <Button variant="secondary" onPress={onXinBaoLuu}>Xin bảo lưu</Button>
          )}
        </View>
      </CardBody>
    </Card>
  );
}

function O({ nhan, giaTri, noiBat }: { nhan: string; giaTri: string; noiBat?: boolean }) {
  return (
    <View style={styles.oItem}>
      <Text style={styles.oLabel}>{nhan}</Text>
      <Text style={[styles.oValue, noiBat && styles.oValueBold]}>{giaTri}</Text>
    </View>
  );
}

function HopThoaiBaoLuu({ hopDong, onClose }: { hopDong: Registration | null; onClose: () => void }) {
  const xinBaoLuu = useXinBaoLuu();
  const [form, setForm] = useState({ fromDate: '', toDate: '', reason: '', reasonType: 'PERSONAL' });

  const gui = () => {
    if (!hopDong) return;
    xinBaoLuu.mutate({ id: hopDong.id, ...form }, { onSuccess: () => { onClose(); setForm({ fromDate: '', toDate: '', reason: '', reasonType: 'PERSONAL' }); } });
  };

  const hopLe = form.fromDate.trim() !== '' && form.toDate.trim() !== '' && form.reason.trim() !== '';

  return (
    <Modal open={!!hopDong} title="Xin bảo lưu gói tập" onClose={onClose}>
      <Alert tone="info">
        Mỗi hợp đồng chỉ được bảo lưu một lần. Ngày hết hạn sẽ được đẩy lùi đúng bằng số ngày bảo lưu.
      </Alert>

      <Input label="Từ ngày (YYYY-MM-DD)" value={form.fromDate} placeholder="2026-10-01"
             onChangeText={(v) => setForm({ ...form, fromDate: v })} />
      <Input label="Đến ngày (YYYY-MM-DD)" value={form.toDate} placeholder="2026-10-15"
             onChangeText={(v) => setForm({ ...form, toDate: v })} />
      <Input label="Lý do cụ thể" value={form.reason} placeholder="Đi công tác nước ngoài 1 tháng"
             onChangeText={(v) => setForm({ ...form, reason: v })} />

      {xinBaoLuu.error instanceof ApiError && (
        <Alert tone="error">
          {xinBaoLuu.error.fields ? Object.values(xinBaoLuu.error.fields).join(', ') : xinBaoLuu.error.message}
        </Alert>
      )}

      <View style={styles.rowEnd}>
        <Button variant="secondary" onPress={onClose}>Hủy</Button>
        <Button onPress={gui} loading={xinBaoLuu.isPending} disabled={!hopLe}>Gửi yêu cầu</Button>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  headRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md },
  h1: { fontSize: 22, fontWeight: '800', color: colors.slate900, flexShrink: 1 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  oItem: { width: '45%' },
  oLabel: { fontSize: 11.5, color: colors.slate500 },
  oValue: { fontSize: 14, fontWeight: '600', color: colors.slate800, marginTop: 2 },
  oValueBold: { color: colors.brand700, fontWeight: '700' },
  freezeBox: { backgroundColor: colors.brand50, borderRadius: 10, padding: spacing.md, gap: 2 },
  freezeTitle: { fontSize: 13, fontWeight: '600', color: colors.brand900 },
  freezeText: { fontSize: 12.5, color: colors.brand700 },
  rowEnd: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing.sm },
});
