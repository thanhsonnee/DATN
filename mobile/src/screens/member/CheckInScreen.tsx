import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useCheckInSelfStatus, useGuiYeuCauTuCheckIn } from '@/hooks/useCheckIn';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Alert';
import { Card, CardBody } from '@/components/ui/Card';
import { Screen } from '@/components/ui/Screen';
import { ApiError } from '@/api/client';
import { colors, spacing } from '@/lib/theme';

type Phase = 'idle' | 'waiting' | 'confirmed' | 'expired';

interface KetQuaCuoi {
  choPhepVao: boolean;
  thongBao: string;
  lyDo: string | null;
}

/**
 * Hội viên tự bấm khi đã có mặt tại phòng tập — thay cho việc lễ tân phải gõ
 * tìm tên. Chỉ đưa vào hàng đợi ở màn hình quầy, lễ tân vẫn là người nhìn ảnh
 * đối chiếu và xác nhận cuối cùng — không tự động mở cửa.
 *
 * Sau khi gửi, màn hình tự poll `/check-ins/self-status` mỗi 3 giây để biết
 * NGAY khi lễ tân đã xác nhận xong (cho vào hoặc từ chối), không cần hội viên
 * tự bấm làm mới hay phải hỏi lễ tân bằng miệng.
 */
export function CheckInScreen() {
  const guiYeuCau = useGuiYeuCauTuCheckIn();
  const [phase, setPhase] = useState<Phase>('idle');
  // Nguồn hiển thị DUY NHẤT cho phase 'confirmed' — đọc trực tiếp từ response vừa nhận
  // (mutation hoặc poll), không suy luận lại từ dữ liệu cache cũ của lần chờ trước.
  const [ketQuaCuoi, setKetQuaCuoi] = useState<KetQuaCuoi | null>(null);

  const trangThai = useCheckInSelfStatus(phase === 'waiting');

  useEffect(() => {
    if (phase !== 'waiting' || !trangThai.data) return;
    if (trangThai.data.status === 'CONFIRMED') {
      setKetQuaCuoi({
        choPhepVao: trangThai.data.choPhepVao,
        thongBao: trangThai.data.thongBao,
        lyDo: trangThai.data.lyDo,
      });
      setPhase('confirmed');
    } else if (trangThai.data.status === 'NONE') {
      setPhase('expired');
    }
  }, [phase, trangThai.data]);

  const guiYeuCauMoi = () => {
    setPhase('idle');
    setKetQuaCuoi(null);
    guiYeuCau.reset();
    guiYeuCau.mutate(undefined, {
      onSuccess: (data) => {
        if (data.result === 'DENIED_ALREADY_INSIDE') {
          // Backend đã biết ngay câu trả lời — hội viên đang ở trong phòng rồi, không cần
          // chờ lễ tân xử lý gì cả. Hiện thẳng kết quả, KHÔNG vào 'waiting' rồi poll, vì
          // poll có thể vô tình khớp với lượt vào THẬT trước đó và hiện nhầm màu xanh.
          setKetQuaCuoi({ choPhepVao: data.choPhepVao, thongBao: data.thongBao, lyDo: null });
          setPhase('confirmed');
        } else {
          setPhase('waiting');
        }
      },
    });
  };

  // Nội dung hiện lên khi đang chờ: ưu tiên dữ liệu poll mới nhất, chưa có thì tạm dùng
  // ngay kết quả preview lúc gửi yêu cầu, để không có khoảng trắng chờ vòng poll đầu tiên.
  const noiDungCho = trangThai.data?.status === 'PENDING' ? trangThai.data : guiYeuCau.data;

  return (
    <Screen scroll={false} style={styles.center}>
      <Text style={styles.h1}>Check-in phòng tập</Text>

      <Card style={styles.card}>
        <CardBody style={styles.body}>
          {phase === 'idle' && (
            <>
              <Text style={styles.hint}>
                Đã có mặt tại phòng tập? Bấm nút bên dưới để báo cho lễ tân — lễ tân sẽ đối
                chiếu ảnh và xác nhận cho bạn vào tập.
              </Text>
              <Button loading={guiYeuCau.isPending} onPress={guiYeuCauMoi}>
                📍 Tôi đã đến phòng tập
              </Button>
              {guiYeuCau.error instanceof ApiError && <Alert tone="error">{guiYeuCau.error.message}</Alert>}
            </>
          )}

          {phase === 'waiting' && (
            <>
              <View style={[styles.resultBox, styles.resultWaiting]}>
                <Text style={[styles.resultText, styles.resultTextWaiting]}>
                  {noiDungCho?.thongBao ?? 'Đang gửi yêu cầu…'}
                </Text>
              </View>
              <Text style={styles.hint}>
                Đã gửi yêu cầu tới quầy lễ tân. Màn hình sẽ tự báo ngay khi lễ tân xác nhận —
                không cần bấm gì thêm. Yêu cầu tự hết hạn sau 3 phút nếu không ai xử lý.
              </Text>
              <Button variant="secondary" onPress={() => setPhase('idle')}>Hủy yêu cầu</Button>
            </>
          )}

          {phase === 'confirmed' && ketQuaCuoi && (
            <>
              <View style={[styles.resultBox, ketQuaCuoi.choPhepVao ? styles.resultOk : styles.resultBad]}>
                <Text style={[styles.resultText, ketQuaCuoi.choPhepVao ? styles.resultTextOk : styles.resultTextBad]}>
                  {ketQuaCuoi.choPhepVao ? '✓ ' : ''}{ketQuaCuoi.thongBao}
                </Text>
                {ketQuaCuoi.lyDo && (
                  <Text style={[styles.resultText, styles.resultTextBad, styles.resultReason]}>
                    Lý do: {ketQuaCuoi.lyDo}
                  </Text>
                )}
              </View>
              <Text style={styles.hint}>
                {ketQuaCuoi.choPhepVao
                  ? 'Lễ tân đã xác nhận — mời bạn vào tập!'
                  : 'Vui lòng ra quầy lễ tân nếu cần hỗ trợ thêm.'}
              </Text>
              <Button variant="secondary" onPress={() => setPhase('idle')}>Quay lại</Button>
            </>
          )}

          {phase === 'expired' && (
            <>
              <View style={[styles.resultBox, styles.resultWaiting]}>
                <Text style={[styles.resultText, styles.resultTextWaiting]}>
                  Yêu cầu đã hết hạn
                </Text>
              </View>
              <Text style={styles.hint}>
                Lễ tân chưa xử lý kịp trong 3 phút. Vui lòng gửi lại.
              </Text>
              <Button onPress={guiYeuCauMoi}>Gửi lại yêu cầu</Button>
            </>
          )}
        </CardBody>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.lg },
  h1: { fontSize: 22, fontWeight: '800', color: colors.slate900, textAlign: 'center' },
  card: { width: '100%', maxWidth: 420 },
  body: { alignItems: 'center', paddingVertical: spacing.xxl },
  hint: { fontSize: 13.5, color: colors.slate500, textAlign: 'center' },
  resultBox: { borderRadius: 10, paddingHorizontal: spacing.lg, paddingVertical: spacing.md, width: '100%' },
  resultOk: { backgroundColor: colors.emerald50 },
  resultBad: { backgroundColor: colors.red50 },
  resultWaiting: { backgroundColor: colors.amber50 },
  resultText: { fontSize: 14, fontWeight: '700', textAlign: 'center' },
  resultTextOk: { color: colors.emerald800 },
  resultTextBad: { color: colors.red700 },
  resultTextWaiting: { color: colors.amber700 },
  resultReason: { marginTop: spacing.xs, fontWeight: '600' },
});
