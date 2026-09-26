import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import {
  useEquipmentList, useMyFeedback, useSubmitFeedback, useUpdateFeedback,
} from '@/hooks/useFeedback';
import { useTrainers } from '@/hooks/useLookup';
import { useRefetchOnFocus } from '@/hooks/useRefetchOnFocus';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Alert';
import { Input } from '@/components/ui/Input';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge, type Tone } from '@/components/ui/Badge';
import { Modal } from '@/components/ui/Modal';
import { EmptyState, Spinner } from '@/components/ui/Spinner';
import { Screen } from '@/components/ui/Screen';
import { LichSuThuGon } from '@/components/ui/LichSuThuGon';
import { ApiError } from '@/api/client';
import { ngayGio, tenLoaiPhanHoi, tenTrangThaiPhanHoi } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';
import type { Feedback, FeedbackType } from '@/api/types';

const LOAI: { value: FeedbackType; nhan: string }[] = [
  { value: 'TRAINER', nhan: 'Đánh giá huấn luyện viên' },
  { value: 'FACILITY', nhan: 'Báo hỏng thiết bị' },
  { value: 'HYGIENE', nhan: 'Vệ sinh' },
  { value: 'SERVICE', nhan: 'Chất lượng dịch vụ' },
  { value: 'GENERAL', nhan: 'Góp ý chung' },
];

function toneTheoTrangThai(status: string): Tone {
  if (status === 'OPEN') return 'amber';
  if (status === 'RESOLVED' || status === 'CLOSED') return 'green';
  return 'blue';
}

// Còn đang xử lý luôn hiện đầy đủ. Đã RESOLVED/CLOSED gộp vào lịch sử thu gọn.
const TRANG_THAI_DANG_XU_LY = new Set(['OPEN', 'IN_PROGRESS', 'WAITING_PARTS']);

export function PhanHoiScreen() {
  const { data: lichSu, isLoading, refetch, isRefetching } = useMyFeedback();
  const { data: trainers } = useTrainers();
  const { data: equipment } = useEquipmentList();
  const guiPhanHoi = useSubmitFeedback();
  const suaPhanHoi = useUpdateFeedback();

  // Admin/lễ tân vừa xử lý phản hồi (thêm resolutionNote, đổi status) — quay
  // lại tab này phải thấy ngay, không cần kéo làm mới tay.
  useRefetchOnFocus(refetch);

  const [loai, setLoai] = useState<FeedbackType>('GENERAL');
  const [trainerId, setTrainerId] = useState<number | ''>('');
  const [equipmentId, setEquipmentId] = useState<number | ''>('');
  const [rating, setRating] = useState(0);
  const [moTa, setMoTa] = useState('');

  const reset = () => { setLoai('GENERAL'); setTrainerId(''); setEquipmentId(''); setRating(0); setMoTa(''); };

  const gui = () => {
    guiPhanHoi.mutate({
      feedbackType: loai,
      trainerId: loai === 'TRAINER' && trainerId !== '' ? trainerId : undefined,
      equipmentId: loai === 'FACILITY' && equipmentId !== '' ? equipmentId : undefined,
      rating: loai === 'TRAINER' && rating > 0 ? rating : undefined,
      description: moTa,
    }, { onSuccess: reset });
  };

  const hopLe = moTa.trim().length > 0
    && (loai !== 'TRAINER' || trainerId !== '')
    && (loai !== 'FACILITY' || equipmentId !== '');

  const [dangSua, setDangSua] = useState<Feedback | null>(null);
  const [moTaSua, setMoTaSua] = useState('');
  const [ratingSua, setRatingSua] = useState(0);

  const moSua = (f: Feedback) => { setDangSua(f); setMoTaSua(f.description); setRatingSua(f.rating ?? 0); };

  const luuSua = () => {
    if (!dangSua) return;
    suaPhanHoi.mutate({
      id: dangSua.id,
      description: moTaSua,
      rating: dangSua.feedbackType === 'TRAINER' && ratingSua > 0 ? ratingSua : undefined,
    }, { onSuccess: () => setDangSua(null) });
  };

  const dangXuLy = lichSu?.filter((f) => TRANG_THAI_DANG_XU_LY.has(f.status)) ?? [];
  const daXong = lichSu?.filter((f) => !TRANG_THAI_DANG_XU_LY.has(f.status)) ?? [];

  const theCard = (f: Feedback) => (
    <Card key={f.id}>
      <CardBody>
        <View style={styles.rowBetween}>
          <View>
            <Text style={styles.itemTitle}>{tenLoaiPhanHoi(f.feedbackType)}</Text>
            <Text style={styles.itemMeta}>{ngayGio(f.createdAt)}</Text>
          </View>
          <Badge tone={toneTheoTrangThai(f.status)}>{tenTrangThaiPhanHoi(f.status)}</Badge>
        </View>
        {f.trainerName && <Text style={styles.itemBody}>HLV: {f.trainerName}{f.rating ? ` · ${f.rating}★` : ''}</Text>}
        {f.equipmentName && <Text style={styles.itemBody}>Thiết bị: {f.equipmentName}</Text>}
        <Text style={styles.itemDesc}>{f.description}</Text>
        {f.resolutionNote && (
          <View style={styles.resolutionBox}>
            <Text style={styles.itemBody}><Text style={{ fontWeight: '700' }}>Phản hồi từ phòng gym: </Text>{f.resolutionNote}</Text>
          </View>
        )}
        <View style={styles.rowEnd}>
          <Button variant="secondary" onPress={() => moSua(f)}>Sửa</Button>
        </View>
      </CardBody>
    </Card>
  );

  return (
    <Screen onRefresh={refetch} refreshing={isRefetching}>
      <Text style={styles.h1}>Phản hồi & góp ý</Text>

      <Card>
        <CardHeader title="Gửi phản hồi mới" subtitle="Đánh giá PT, báo thiết bị hỏng, hoặc góp ý chung" />
        <CardBody>
          <View style={{ gap: spacing.xs }}>
            <Text style={styles.label}>Loại phản hồi</Text>
            <View style={{ gap: spacing.xs }}>
              {LOAI.map((l) => (
                <Pressable key={l.value} onPress={() => setLoai(l.value)}
                           style={[styles.pickRow, loai === l.value && styles.pickRowActive]}>
                  <Text style={[styles.pickRowText, loai === l.value && styles.pickRowTextActive]}>{l.nhan}</Text>
                </Pressable>
              ))}
            </View>
          </View>

          {loai === 'TRAINER' && (
            <View style={{ gap: spacing.sm }}>
              <View style={{ gap: spacing.xs }}>
                <Text style={styles.label}>Huấn luyện viên</Text>
                <View style={{ gap: spacing.xs }}>
                  {trainers?.map((t) => (
                    <Pressable key={t.id} onPress={() => setTrainerId(t.id)}
                               style={[styles.pickRow, trainerId === t.id && styles.pickRowActive]}>
                      <Text style={[styles.pickRowText, trainerId === t.id && styles.pickRowTextActive]}>{t.fullName}</Text>
                    </Pressable>
                  ))}
                </View>
              </View>
              <SaoChon nhan="Đánh giá (không bắt buộc)" rating={rating} onChange={setRating} />
            </View>
          )}

          {loai === 'FACILITY' && (
            <View style={{ gap: spacing.xs }}>
              <Text style={styles.label}>Thiết bị bị lỗi</Text>
              <View style={{ gap: spacing.xs }}>
                {equipment?.map((eq) => (
                  <Pressable key={eq.id} onPress={() => setEquipmentId(eq.id)}
                             style={[styles.pickRow, equipmentId === eq.id && styles.pickRowActive]}>
                    <Text style={[styles.pickRowText, equipmentId === eq.id && styles.pickRowTextActive]}>
                      {eq.name}{eq.roomName ? ` · ${eq.roomName}` : ''}
                    </Text>
                  </Pressable>
                ))}
              </View>
            </View>
          )}

          <Input label="Nội dung" value={moTa} onChangeText={setMoTa} multiline numberOfLines={3}
                 placeholder="Mô tả chi tiết…" style={{ minHeight: 80, textAlignVertical: 'top' }} />

          {guiPhanHoi.error instanceof ApiError && <Alert tone="error">{guiPhanHoi.error.message}</Alert>}
          {guiPhanHoi.isSuccess && <Alert tone="success">Đã gửi phản hồi, cảm ơn bạn!</Alert>}

          <Button onPress={gui} loading={guiPhanHoi.isPending} disabled={!hopLe}>Gửi phản hồi</Button>
        </CardBody>
      </Card>

      <View style={{ gap: spacing.md }}>
        <Text style={styles.h2}>Đang chờ xử lý</Text>
        {isLoading ? <Spinner /> : lichSu?.length === 0 ? <EmptyState title="Chưa gửi phản hồi nào" /> : (
          dangXuLy.length === 0
            ? <Text style={styles.hintText}>Không có phản hồi nào đang chờ xử lý.</Text>
            : dangXuLy.map(theCard)
        )}
      </View>

      <LichSuThuGon tieuDe="Phản hồi đã xử lý xong" soLuong={daXong.length}>
        {daXong.map(theCard)}
      </LichSuThuGon>

      <Modal open={!!dangSua} title="Sửa phản hồi" onClose={() => setDangSua(null)}>
        {dangSua?.feedbackType === 'TRAINER' && (
          <SaoChon nhan={`Đánh giá lại HLV ${dangSua.trainerName}`} rating={ratingSua} onChange={setRatingSua} />
        )}
        <Input label="Nội dung" value={moTaSua} onChangeText={setMoTaSua} multiline numberOfLines={3}
               style={{ minHeight: 80, textAlignVertical: 'top' }} />
        {suaPhanHoi.error instanceof ApiError && <Alert tone="error">{suaPhanHoi.error.message}</Alert>}
        <View style={styles.rowEnd}>
          <Button variant="secondary" onPress={() => setDangSua(null)}>Đóng</Button>
          <Button loading={suaPhanHoi.isPending} disabled={!moTaSua.trim()} onPress={luuSua}>Lưu thay đổi</Button>
        </View>
      </Modal>
    </Screen>
  );
}

function SaoChon({ nhan, rating, onChange }: { nhan: string; rating: number; onChange: (n: number) => void }) {
  return (
    <View style={{ gap: spacing.xs }}>
      <Text style={styles.label}>{nhan}</Text>
      <View style={{ flexDirection: 'row', gap: 4 }}>
        {[1, 2, 3, 4, 5].map((sao) => (
          <Pressable key={sao} onPress={() => onChange(sao === rating ? 0 : sao)} hitSlop={6}>
            <Text style={[styles.star, sao <= rating && styles.starActive]}>★</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: 22, fontWeight: '800', color: colors.slate900 },
  h2: { fontSize: 15, fontWeight: '700', color: colors.slate800 },
  hintText: { fontSize: 13, color: colors.slate500 },
  label: { fontSize: 13, fontWeight: '600', color: colors.slate700 },
  pickRow: { borderWidth: 1, borderColor: colors.slate200, borderRadius: 10, paddingHorizontal: spacing.md, paddingVertical: spacing.sm + 2 },
  pickRowActive: { borderColor: colors.brand500, backgroundColor: colors.brand50 },
  pickRowText: { fontSize: 14, color: colors.slate700 },
  pickRowTextActive: { color: colors.brand700, fontWeight: '600' },
  star: { fontSize: 26, color: colors.slate200 },
  starActive: { color: colors.amber400 },
  rowBetween: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing.sm },
  itemTitle: { fontSize: 14, fontWeight: '700', color: colors.slate900 },
  itemMeta: { fontSize: 11.5, color: colors.slate500, marginTop: 2 },
  itemBody: { fontSize: 13, color: colors.slate600 },
  itemDesc: { fontSize: 13.5, color: colors.slate700 },
  resolutionBox: { backgroundColor: colors.slate50, borderRadius: 8, padding: spacing.sm },
  rowEnd: { flexDirection: 'row', justifyContent: 'flex-end' },
});
