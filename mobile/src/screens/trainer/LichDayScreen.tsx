import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import {
  useDuyetLich, useHlvXacNhan, useTrainerSchedule, useTuChoiLich, useVangMat,
} from '@/hooks/usePtSessions';
import { useRefetchOnFocus } from '@/hooks/useRefetchOnFocus';
import { TheBuoiTap } from '@/components/TheBuoiTap';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/ui/Alert';
import { Modal } from '@/components/ui/Modal';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { EmptyState, Spinner } from '@/components/ui/Spinner';
import { Screen } from '@/components/ui/Screen';
import { LichSuThuGon } from '@/components/ui/LichSuThuGon';
import { LichThang } from '@/components/ui/LichThang';
import { ApiError } from '@/api/client';
import { ngay, ngayIsoDiaPhuong } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';
import type { PtSession } from '@/api/types';

/** Lịch dạy của huấn luyện viên: duyệt yêu cầu, xem lịch sắp tới, xác nhận đã dạy. */
export function LichDayScreen() {
  const { data: buoiTaps, isLoading, refetch, isRefetching } = useTrainerSchedule();
  const duyet = useDuyetLich();
  const tuChoi = useTuChoiLich();
  const xacNhan = useHlvXacNhan();
  const vangMat = useVangMat();

  // Hội viên vừa đặt lịch mới từ app của họ — quay lại tab này phải thấy ngay.
  useRefetchOnFocus(refetch);

  const [tuChoiId, setTuChoiId] = useState<number | null>(null);
  const [lyDo, setLyDo] = useState('');
  const [xemTheoLich, setXemTheoLich] = useState(false);
  const [ngayDuocChon, setNgayDuocChon] = useState<string | null>(null);

  const { choDuyet, sapToi, lichSu } = useMemo(() => {
    const list = buoiTaps ?? [];
    return {
      choDuyet: list.filter((b) => b.status === 'PENDING_TRAINER'),
      sapToi: list
        .filter((b) => b.status === 'SCHEDULED')
        .sort((a, b) => new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime()),
      lichSu: list
        .filter((b) => ['COMPLETED', 'NO_SHOW', 'CANCELLED', 'REJECTED'].includes(b.status))
        .sort((a, b) => new Date(b.scheduledStart).getTime() - new Date(a.scheduledStart).getTime()),
    };
  }, [buoiTaps]);

  // key = "YYYY-MM-DD", value = ngày đó có buổi đang chờ duyệt hay không — để chấm trên lịch
  // tháng đổi màu, PT thấy ngay ngày nào cần xử lý thay vì chỉ biết "có lịch".
  const ngayCoDanhDau = useMemo(() => {
    const m = new Map<string, boolean>();
    (buoiTaps ?? []).forEach((b) => {
      const key = ngayIsoDiaPhuong(new Date(b.scheduledStart));
      m.set(key, (m.get(key) ?? false) || b.status === 'PENDING_TRAINER');
    });
    return m;
  }, [buoiTaps]);

  const buoiTheoNgayDuocChon = useMemo(() => {
    if (!ngayDuocChon) return [];
    return (buoiTaps ?? [])
      .filter((b) => ngayIsoDiaPhuong(new Date(b.scheduledStart)) === ngayDuocChon)
      .sort((a, b) => new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime());
  }, [buoiTaps, ngayDuocChon]);

  const hanhDongChoBuoi = (b: PtSession) => {
    if (b.status === 'PENDING_TRAINER') {
      return (
        <>
          <Button variant="secondary" onPress={() => setTuChoiId(b.id)}>Từ chối</Button>
          <Button loading={duyet.isPending} onPress={() => duyet.mutate(b.id)}>Nhận lịch</Button>
        </>
      );
    }
    if (b.status === 'SCHEDULED') {
      return (
        <>
          <Button
            variant="secondary"
            onPress={() => vangMat.mutate({ id: b.id, noShowBy: 'MEMBER', note: 'Hội viên không đến' })}
          >
            Hội viên vắng
          </Button>
          {!b.trainerConfirmedAt ? (
            <Button loading={xacNhan.isPending} onPress={() => xacNhan.mutate(b.id)}>Đã dạy xong</Button>
          ) : (
            <Text style={styles.waitingText}>Đã xác nhận, chờ hội viên phản hồi</Text>
          )}
        </>
      );
    }
    return undefined;
  };

  if (isLoading) return <Spinner label="Đang tải lịch dạy…" />;

  return (
    <Screen onRefresh={refetch} refreshing={isRefetching}>
      <View style={styles.headerRow}>
        <Text style={styles.h1}>Lịch dạy của tôi</Text>
        <Pressable
          style={styles.toggleBtn}
          onPress={() => { setXemTheoLich((v) => !v); setNgayDuocChon(null); }}
        >
          <Text style={styles.toggleBtnText}>{xemTheoLich ? '📋 Danh sách' : '📅 Lịch tháng'}</Text>
        </Pressable>
      </View>

      <Card>
        <CardHeader title="Cách tính công buổi tập" />
        <CardBody>
          <Text style={styles.noteText}>
            Buổi tập chỉ được tính công khi <Text style={styles.bold}>cả hai bên cùng xác nhận</Text>.
            Bấm "Đã dạy xong" là điều kiện khởi động — không bấm thì buổi không dạy sẽ không bao giờ
            tự động được trả công. Hội viên không phản hồi trong 24 giờ thì hệ thống tự duyệt, nhưng
            có đánh dấu để kiểm toán.
          </Text>
        </CardBody>
      </Card>

      {(duyet.error instanceof ApiError || xacNhan.error instanceof ApiError || vangMat.error instanceof ApiError) && (
        <Alert tone="error">
          {(duyet.error as ApiError | undefined)?.message
            ?? (xacNhan.error as ApiError | undefined)?.message
            ?? (vangMat.error as ApiError | undefined)?.message}
        </Alert>
      )}

      {xemTheoLich ? (
        <>
          <Card>
            <CardBody>
              <LichThang ngayCoDanhDau={ngayCoDanhDau} ngayDuocChon={ngayDuocChon} onChonNgay={setNgayDuocChon} />
            </CardBody>
          </Card>

          <View style={{ gap: spacing.md }}>
            <Text style={styles.h2}>
              {ngayDuocChon
                ? `Lịch ngày ${ngay(ngayDuocChon)} (${buoiTheoNgayDuocChon.length})`
                : 'Chọn 1 ngày trên lịch để xem chi tiết'}
            </Text>
            {ngayDuocChon && buoiTheoNgayDuocChon.length === 0 && (
              <EmptyState title="Không có buổi tập nào trong ngày này" />
            )}
            {buoiTheoNgayDuocChon.map((b) => (
              <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName} actions={hanhDongChoBuoi(b)} />
            ))}
          </View>
        </>
      ) : (
        <>
          {choDuyet.length > 0 && (
            <View style={{ gap: spacing.md }}>
              <Text style={styles.h2}>Chờ bạn duyệt ({choDuyet.length})</Text>
              {choDuyet.map((b) => (
                <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName} actions={hanhDongChoBuoi(b)} />
              ))}
            </View>
          )}

          <View style={{ gap: spacing.md }}>
            <Text style={styles.h2}>Sắp tới ({sapToi.length})</Text>
            {sapToi.length === 0 && <EmptyState title="Không có buổi nào sắp tới" />}
            {sapToi.map((b) => (
              <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName} actions={hanhDongChoBuoi(b)} />
            ))}
          </View>

          <LichSuThuGon tieuDe="Lịch sử" soLuong={lichSu.length}>
            {lichSu.map((b: PtSession) => <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName} />)}
          </LichSuThuGon>
        </>
      )}

      <Modal open={tuChoiId != null} title="Từ chối yêu cầu đặt lịch" onClose={() => setTuChoiId(null)}>
        <Input label="Lý do từ chối" value={lyDo} onChangeText={setLyDo}
               placeholder="Trùng lịch với học viên khác" hint="Hội viên cần biết lý do để xếp lại lịch" />
        {tuChoi.error instanceof ApiError && <Alert tone="error">{tuChoi.error.message}</Alert>}
        <View style={styles.rowEnd}>
          <Button variant="secondary" onPress={() => setTuChoiId(null)}>Đóng</Button>
          <Button
            variant="danger" loading={tuChoi.isPending} disabled={!lyDo.trim()}
            onPress={() => tuChoiId && tuChoi.mutate(
              { id: tuChoiId, reason: lyDo },
              { onSuccess: () => { setTuChoiId(null); setLyDo(''); } },
            )}
          >
            Từ chối
          </Button>
        </View>
      </Modal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  h1: { fontSize: 22, fontWeight: '800', color: colors.slate900 },
  h2: { fontSize: 15, fontWeight: '700', color: colors.slate800 },
  noteText: { fontSize: 13, color: colors.slate600, lineHeight: 19 },
  bold: { fontWeight: '700', color: colors.slate800 },
  waitingText: { fontSize: 12, color: colors.slate500, alignSelf: 'center' },
  rowEnd: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing.sm },
  toggleBtn: {
    backgroundColor: colors.brand50, borderRadius: 999,
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm,
  },
  toggleBtnText: { fontSize: 13, fontWeight: '700', color: colors.brand700 },
});
