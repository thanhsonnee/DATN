import { StyleSheet, Text, View } from 'react-native';
import { useSoCai } from '@/hooks/usePtSessions';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Alert } from '@/components/ui/Alert';
import { Spinner } from '@/components/ui/Spinner';
import { ngayGio, tenButToan, tenNguonButToan } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';

/**
 * Sổ cái buổi tập của một hợp đồng — hiển thị cả số dư lẫn lịch sử, để hội
 * viên không chỉ biết còn mấy buổi mà còn thấy vì sao còn ngần ấy.
 */
export function SoCaiBuoiTap({ registrationId }: { registrationId: number }) {
  const { data: soCai, isLoading } = useSoCai(registrationId);

  if (isLoading) return <Spinner label="Đang tải sổ cái…" />;
  if (!soCai) return null;

  return (
    <Card>
      <CardHeader
        title="Sổ cái buổi tập"
        subtitle="Mọi biến động số buổi đều được ghi lại, không sửa không xóa"
        action={
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={styles.balance}>{soCai.soDuHienTai}</Text>
            <Text style={styles.balanceLabel}>buổi còn lại</Text>
          </View>
        }
      />
      <CardBody>
        {!soCai.batBienConDung && (
          <Alert tone="error">Sổ cái sai lệch: tổng các bút toán không khớp số dư cuối. Cần kiểm tra ngay.</Alert>
        )}

        {soCai.lichSu.length === 0 ? (
          <Text style={styles.empty}>Chưa có biến động nào. Sổ cái được cấp buổi khi hợp đồng kích hoạt.</Text>
        ) : (
          soCai.lichSu.map((e) => (
            <View key={e.id} style={styles.row}>
              <View style={{ flex: 1 }}>
                <Text style={styles.rowTime}>{ngayGio(e.createdAt)}</Text>
                <Text style={styles.rowSource}>
                  {tenNguonButToan(e.sourceType)}{e.sourceId ? ` #${e.sourceId}` : ''}
                  {e.reason ? ` · ${e.reason}` : ''}
                </Text>
              </View>
              <View style={{ alignItems: 'flex-end', gap: 3 }}>
                <Badge tone={e.delta > 0 ? 'green' : 'amber'}>{tenButToan(e.entryType)}</Badge>
                <Text style={[styles.delta, e.delta > 0 ? styles.deltaPos : styles.deltaNeg]}>
                  {e.delta > 0 ? '+' : ''}{e.delta} → {e.balanceAfter}
                </Text>
              </View>
            </View>
          ))
        )}
      </CardBody>
    </Card>
  );
}

const styles = StyleSheet.create({
  balance: { fontSize: 22, fontWeight: '800', color: colors.brand700 },
  balanceLabel: { fontSize: 11, color: colors.slate500 },
  empty: { fontSize: 13, color: colors.slate500, textAlign: 'center', paddingVertical: spacing.lg },
  row: {
    flexDirection: 'row', gap: spacing.sm, paddingVertical: spacing.sm,
    borderTopWidth: 1, borderTopColor: colors.slate100,
  },
  rowTime: { fontSize: 12.5, color: colors.slate500 },
  rowSource: { fontSize: 11.5, color: colors.slate400, marginTop: 2 },
  delta: { fontSize: 12.5, fontWeight: '700' },
  deltaPos: { color: colors.emerald700 },
  deltaNeg: { color: colors.amber700 },
});
