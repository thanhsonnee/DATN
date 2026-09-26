import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, spacing } from '@/lib/theme';

/**
 * Khối lịch sử thu gọn — mặc định đóng, bấm để mở. Dùng cho phần dữ liệu đã
 * kết thúc/đã xử lý xong, để không chiếm chỗ mặc định khi danh sách dài dần
 * theo thời gian. Cùng cách làm với `GoiCuaToiPage` (web).
 */
export function LichSuThuGon({ tieuDe, soLuong, children }: {
  tieuDe: string;
  soLuong: number;
  children: React.ReactNode;
}) {
  const [mo, setMo] = useState(false);

  if (soLuong === 0) return null;

  return (
    <View style={styles.wrap}>
      <Pressable onPress={() => setMo((v) => !v)} hitSlop={8}>
        <Text style={styles.toggle}>{mo ? '▾' : '▸'} {tieuDe} ({soLuong})</Text>
      </Pressable>

      {mo && <View style={styles.content}>{children}</View>}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { borderTopWidth: 1, borderTopColor: colors.slate200, paddingTop: spacing.md, gap: spacing.md },
  toggle: { fontSize: 13.5, fontWeight: '600', color: colors.slate600 },
  content: { gap: spacing.md },
});
