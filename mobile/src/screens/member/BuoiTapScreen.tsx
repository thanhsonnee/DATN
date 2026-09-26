import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import {
  useDatLich, useHoiVienXacNhan, useHuyBuoi, useMyPtSessions,
} from '@/hooks/usePtSessions';
import { useMyRegistrations } from '@/hooks/useRegistrations';
import { useAvailableTrainers, useTrainers } from '@/hooks/useLookup';
import { useRefetchOnFocus } from '@/hooks/useRefetchOnFocus';
import { TheBuoiTap } from '@/components/TheBuoiTap';
import { SoCaiBuoiTap } from '@/components/SoCaiBuoiTap';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { DateTimeField } from '@/components/ui/DateTimeField';
import { Alert } from '@/components/ui/Alert';
import { Modal } from '@/components/ui/Modal';
import { EmptyState, Spinner } from '@/components/ui/Spinner';
import { Screen } from '@/components/ui/Screen';
import { LichSuThuGon } from '@/components/ui/LichSuThuGon';
import { ApiError } from '@/api/client';
import { colors, spacing } from '@/lib/theme';
import type { PtSession } from '@/api/types';

// Buổi còn actionable (chờ duyệt/chờ xác nhận) luôn hiện đầy đủ. Buổi đã ngã
// ngũ gộp vào "Lịch sử" thu gọn — không dài vô hạn theo thời gian.
const TRANG_THAI_CAN_CHU_Y = new Set(['PENDING_TRAINER', 'SCHEDULED']);

export function BuoiTapScreen() {
  const { data: buoiTaps, isLoading, refetch, isRefetching } = useMyPtSessions();
  const { data: hopDongs } = useMyRegistrations();
  const xacNhan = useHoiVienXacNhan();
  const huyBuoi = useHuyBuoi();
  const qc = useQueryClient();

  // PT vừa duyệt/từ chối/xác nhận đã dạy — quay lại tab này phải thấy trạng
  // thái mới ngay, cả lịch tập lẫn sổ cái (số buổi thay đổi khi buổi hoàn thành).
  useRefetchOnFocus(() => {
    refetch();
    qc.invalidateQueries({ queryKey: ['so-cai'] });
  });

  const [moDatLich, setMoDatLich] = useState(false);
  const [huyBuoiId, setHuyBuoiId] = useState<number | null>(null);
  const [lyDoHuy, setLyDoHuy] = useState('');

  const hopDongCoPt = hopDongs?.filter(
    (h) => h.status === 'ACTIVE' && h.sessionsTotal != null && h.sessionsTotal > 0,
  ) ?? [];

  const dangCanChuY = buoiTaps?.filter((b) => TRANG_THAI_CAN_CHU_Y.has(b.status)) ?? [];
  const daKetThuc = buoiTaps?.filter((b) => !TRANG_THAI_CAN_CHU_Y.has(b.status)) ?? [];

  const hanhDongCho = (b: PtSession) => (
    <>
      {b.status === 'SCHEDULED' && b.trainerConfirmedAt && !b.memberConfirmedAt && (
        <Button loading={xacNhan.isPending} onPress={() => xacNhan.mutate(b.id)}>Xác nhận đã tập</Button>
      )}
      {b.status === 'SCHEDULED' && !b.trainerConfirmedAt && (
        <Text style={styles.waitingText}>Chờ huấn luyện viên bấm kết thúc buổi tập</Text>
      )}
      <Button variant="secondary" onPress={() => setHuyBuoiId(b.id)}>Hủy buổi</Button>
    </>
  );

  if (isLoading) return <Spinner label="Đang tải lịch tập…" />;

  return (
    <Screen onRefresh={refetch} refreshing={isRefetching}>
      <View style={styles.headRow}>
        <Text style={styles.h1}>Buổi tập với huấn luyện viên</Text>
        {hopDongCoPt.length > 0 && <Button onPress={() => setMoDatLich(true)}>Đặt lịch mới</Button>}
      </View>

      {hopDongCoPt.length === 0 && (
        <Alert tone="info">Bạn chưa có gói tập nào kèm buổi PT. Ra quầy lễ tân hoặc vào website để mua gói.</Alert>
      )}

      {hopDongCoPt.map((h) => <SoCaiBuoiTap key={h.id} registrationId={h.id} />)}

      <View style={{ gap: spacing.md }}>
        <Text style={styles.h2}>Lịch tập sắp tới</Text>

        {buoiTaps?.length === 0 && <EmptyState title="Chưa có buổi tập nào" />}
        {buoiTaps && buoiTaps.length > 0 && dangCanChuY.length === 0 && (
          <Text style={styles.hintText}>Không có buổi nào sắp tới hoặc đang chờ duyệt.</Text>
        )}

        {dangCanChuY.map((b) => (
          <TheBuoiTap key={b.id} buoi={b} doiTac={b.trainerName} actions={hanhDongCho(b)} />
        ))}
      </View>

      <LichSuThuGon tieuDe="Lịch sử buổi tập" soLuong={daKetThuc.length}>
        {daKetThuc.map((b) => <TheBuoiTap key={b.id} buoi={b} doiTac={b.trainerName} />)}
      </LichSuThuGon>

      {xacNhan.error instanceof ApiError && <Alert tone="error">{xacNhan.error.message}</Alert>}

      <HopThoaiDatLich
        open={moDatLich} onClose={() => setMoDatLich(false)}
        hopDongs={hopDongCoPt.map((h) => ({ id: h.id, ten: h.membershipName }))}
      />

      <Modal open={huyBuoiId != null} title="Hủy buổi tập" onClose={() => setHuyBuoiId(null)}>
        <Alert tone="info">Hủy trước giờ hẹn ít nhất 4 tiếng thì buổi được hoàn lại. Hủy sát giờ sẽ mất buổi.</Alert>
        <Input label="Lý do hủy" value={lyDoHuy} onChangeText={setLyDoHuy} placeholder="Bận đột xuất" />
        {huyBuoi.error instanceof ApiError && <Alert tone="error">{huyBuoi.error.message}</Alert>}
        <View style={styles.rowEnd}>
          <Button variant="secondary" onPress={() => setHuyBuoiId(null)}>Đóng</Button>
          <Button
            variant="danger" loading={huyBuoi.isPending}
            onPress={() => huyBuoiId && huyBuoi.mutate(
              { id: huyBuoiId, cancelledBy: 'MEMBER', reason: lyDoHuy },
              { onSuccess: () => { setHuyBuoiId(null); setLyDoHuy(''); } },
            )}
          >
            Xác nhận hủy
          </Button>
        </View>
      </Modal>
    </Screen>
  );
}

function HopThoaiDatLich({ open, onClose, hopDongs }: {
  open: boolean; onClose: () => void; hopDongs: { id: number; ten: string }[];
}) {
  const datLich = useDatLich();
  const { data: trainers, isLoading: dangTaiTrainers } = useTrainers();
  const [form, setForm] = useState({ registrationId: 0, trainerId: 0, roomName: 'Khu tạ tự do' });
  const [ngayGio, setNgayGio] = useState<Date | null>(null);

  // Cho phép chọn HLV trước hoặc ngày/giờ trước. Một khi đã có ngày/giờ, chỉ
  // hiện HLV còn rảnh trong khung giờ đó, để không chọn nhầm người chắc chắn
  // sẽ bị từ chối vì trùng lịch đã chốt.
  const { batDauISO, ketThucISO } = useMemo(() => {
    if (!ngayGio) return { batDauISO: null, ketThucISO: null };
    const ketThuc = new Date(ngayGio.getTime() + 60 * 60 * 1000);
    return { batDauISO: ngayGio.toISOString(), ketThucISO: ketThuc.toISOString() };
  }, [ngayGio]);

  const daChonGioTruoc = batDauISO != null;
  const { data: trainerRanh, isLoading: dangLocTrainer } = useAvailableTrainers(batDauISO, ketThucISO);
  const danhSachTrainer = daChonGioTruoc ? trainerRanh : trainers;
  const dangTaiDanhSach = daChonGioTruoc ? dangLocTrainer : dangTaiTrainers;

  useEffect(() => {
    if (daChonGioTruoc && trainerRanh && form.trainerId
        && !trainerRanh.some((t) => t.id === form.trainerId)) {
      setForm((f) => ({ ...f, trainerId: 0 }));
    }
  }, [trainerRanh, daChonGioTruoc, form.trainerId]);

  const gui = () => {
    if (!batDauISO || !ketThucISO) return;
    datLich.mutate({
      registrationId: form.registrationId || hopDongs[0]?.id,
      trainerId: form.trainerId,
      scheduledStart: batDauISO,
      scheduledEnd: ketThucISO,
      roomName: form.roomName,
    }, { onSuccess: () => { onClose(); setForm({ registrationId: 0, trainerId: 0, roomName: 'Khu tạ tự do' }); setNgayGio(null); } });
  };

  return (
    <Modal open={open} title="Đặt lịch tập" onClose={onClose}>
      <Alert tone="info">
        Buổi tập sẽ ở trạng thái chờ huấn luyện viên duyệt. Số buổi chưa bị trừ — chỉ trừ khi buổi thực sự hoàn thành.
      </Alert>

      {hopDongs.length > 1 && (
        <View style={{ gap: spacing.xs }}>
          <Text style={styles.label}>Dùng gói</Text>
          <View style={{ gap: spacing.xs }}>
            {hopDongs.map((h) => (
              <Pressable key={h.id} onPress={() => setForm({ ...form, registrationId: h.id })}
                         style={[styles.pickRow, (form.registrationId || hopDongs[0]?.id) === h.id && styles.pickRowActive]}>
                <Text style={styles.pickRowText}>{h.ten}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      )}

      <View style={styles.gridTwo}>
        <View style={{ flex: 1 }}>
          <DateTimeField label="Ngày tập" mode="date" value={ngayGio} minimumDate={new Date()}
                         onChange={(d) => setNgayGio((cur) => ganNgayGio(cur, d, 'date'))} />
        </View>
        <View style={{ flex: 1 }}>
          <DateTimeField label="Giờ bắt đầu" mode="time" value={ngayGio}
                         onChange={(d) => setNgayGio((cur) => ganNgayGio(cur, d, 'time'))} />
        </View>
      </View>

      <View style={{ gap: spacing.xs }}>
        <Text style={styles.label}>Chọn huấn luyện viên</Text>
        {daChonGioTruoc && !dangTaiDanhSach && (
          <Text style={styles.hintText}>Đang hiện huấn luyện viên còn rảnh vào khung giờ bạn đã chọn.</Text>
        )}
        {dangTaiDanhSach ? (
          <Text style={styles.hintText}>Đang tải danh sách…</Text>
        ) : danhSachTrainer?.length === 0 ? (
          <Text style={styles.warnInlineText}>Không có huấn luyện viên nào rảnh vào khung giờ này, vui lòng chọn giờ khác.</Text>
        ) : (
          <View style={{ gap: spacing.xs }}>
            {danhSachTrainer?.map((tr) => (
              <Pressable key={tr.id} onPress={() => setForm({ ...form, trainerId: tr.id })}
                         style={[styles.pickRow, form.trainerId === tr.id && styles.pickRowActive]}>
                <Text style={styles.pickRowText}>{tr.fullName}</Text>
                <Text style={styles.pickRowMeta}>
                  {tr.level ? `${tr.level} · ` : ''}
                  {tr.ratingAvg != null ? `★ ${tr.ratingAvg.toFixed(1)} (${tr.ratingCount} đánh giá)` : 'Chưa có đánh giá'}
                </Text>
              </Pressable>
            ))}
          </View>
        )}
      </View>

      <Input label="Phòng tập" value={form.roomName} onChangeText={(v) => setForm({ ...form, roomName: v })} />

      {datLich.error instanceof ApiError && <Alert tone="error">{datLich.error.message}</Alert>}

      <View style={styles.rowEnd}>
        <Button variant="secondary" onPress={onClose}>Hủy</Button>
        <Button onPress={gui} loading={datLich.isPending} disabled={!ngayGio || !form.trainerId}>Gửi yêu cầu</Button>
      </View>
    </Modal>
  );
}

/**
 * Ghép phần vừa chọn (ngày hoặc giờ) vào Date hiện có, giữ nguyên phần còn lại.
 * Chưa có gì trước đó thì lấy mốc mặc định hôm nay 19:00, rồi ghi đè đúng phần vừa chọn.
 */
function ganNgayGio(hienTai: Date | null, chon: Date, phan: 'date' | 'time'): Date {
  const macDinh = new Date();
  macDinh.setHours(19, 0, 0, 0);
  const ket = new Date(hienTai ?? macDinh);
  if (phan === 'date') ket.setFullYear(chon.getFullYear(), chon.getMonth(), chon.getDate());
  else ket.setHours(chon.getHours(), chon.getMinutes(), 0, 0);
  return ket;
}

const styles = StyleSheet.create({
  headRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md },
  h1: { fontSize: 20, fontWeight: '800', color: colors.slate900, flexShrink: 1 },
  h2: { fontSize: 15, fontWeight: '700', color: colors.slate800 },
  waitingText: { fontSize: 12, color: colors.slate500, alignSelf: 'center' },
  rowEnd: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing.sm },
  gridTwo: { flexDirection: 'row', gap: spacing.md },
  label: { fontSize: 13, fontWeight: '600', color: colors.slate700 },
  hintText: { fontSize: 12, color: colors.slate500 },
  warnInlineText: { fontSize: 13, color: colors.amber700 },
  pickRow: {
    borderWidth: 1, borderColor: colors.slate200, borderRadius: 10,
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm + 2,
  },
  pickRowActive: { borderColor: colors.brand500, backgroundColor: colors.brand50 },
  pickRowText: { fontSize: 14, fontWeight: '600', color: colors.slate900 },
  pickRowMeta: { fontSize: 11.5, color: colors.slate500, marginTop: 2 },
});
