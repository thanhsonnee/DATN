import { useMemo, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useFeedbackAboutMe } from '@/hooks/useFeedback';
import { useRefetchOnFocus } from '@/hooks/useRefetchOnFocus';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState, Spinner } from '@/components/ui/Spinner';
import { Screen } from '@/components/ui/Screen';
import { ngayGio } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';

// Không có "còn cần xử lý"/"đã xong" ở đây (chỉ đọc, không hành động) — nên
// thu gọn kiểu khác: hiện N gần nhất, bấm "Xem thêm" mới hiện tiếp.
const SO_LUONG_BAN_DAU = 5;
const SO_LUONG_MOI_LAN_XEM_THEM = 10;

/** Huấn luyện viên xem lại đánh giá hội viên gửi về mình — chỉ đọc, không sửa được. */
export function DanhGiaCuaToiScreen() {
  const { data: danhGia, isLoading, refetch, isRefetching } = useFeedbackAboutMe();

  // Hội viên vừa gửi đánh giá mới — quay lại tab này phải thấy ngay.
  useRefetchOnFocus(refetch);

  const [soHien, setSoHien] = useState(SO_LUONG_BAN_DAU);
  const danhSachHien = danhGia?.slice(0, soHien) ?? [];
  const conLai = (danhGia?.length ?? 0) - danhSachHien.length;

  const { coSao, diemTB } = useMemo(() => {
    const coSao = danhGia?.filter((f) => f.rating != null) ?? [];
    const diemTB = coSao.length > 0
      ? (coSao.reduce((tong, f) => tong + (f.rating ?? 0), 0) / coSao.length).toFixed(1)
      : null;
    return { coSao, diemTB };
  }, [danhGia]);

  if (isLoading) return <Spinner label="Đang tải đánh giá…" />;

  return (
    <Screen onRefresh={refetch} refreshing={isRefetching}>
      <Text style={styles.h1}>Đánh giá về tôi</Text>

      <Card>
        <CardHeader
          title="Điểm trung bình"
          subtitle={`Tính từ ${coSao.length} lượt đánh giá có chấm sao`}
          action={
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={styles.avg}>{diemTB ?? '—'}</Text>
              <Text style={styles.avgLabel}>/ 5 sao</Text>
            </View>
          }
        />
      </Card>

      <View style={{ gap: spacing.md }}>
        {danhGia?.length === 0 ? (
          <EmptyState title="Chưa có hội viên nào gửi đánh giá" />
        ) : (
          danhSachHien.map((f) => (
            <Card key={f.id}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.name}>{f.memberName}</Text>
                  <View style={{ alignItems: 'flex-end', gap: 2 }}>
                    {f.rating != null && (
                      <Text style={styles.stars}>{'★'.repeat(f.rating)}{'☆'.repeat(5 - f.rating)}</Text>
                    )}
                    <Text style={styles.date}>{ngayGio(f.createdAt)}</Text>
                  </View>
                </View>
                <Text style={styles.desc}>{f.description}</Text>
              </CardBody>
            </Card>
          ))
        )}

        {conLai > 0 && (
          <Button variant="secondary" onPress={() => setSoHien((n) => n + SO_LUONG_MOI_LAN_XEM_THEM)}>
            {`Xem thêm (còn ${conLai})`}
          </Button>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: 22, fontWeight: '800', color: colors.slate900 },
  avg: { fontSize: 22, fontWeight: '800', color: colors.brand700 },
  avgLabel: { fontSize: 11, color: colors.slate500 },
  row: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing.sm },
  name: { fontSize: 14, fontWeight: '700', color: colors.slate900 },
  stars: { fontSize: 13, color: colors.amber400 },
  date: { fontSize: 11, color: colors.slate500 },
  desc: { fontSize: 13.5, color: colors.slate700 },
});
