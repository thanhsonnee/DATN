import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ngayIsoDiaPhuong } from '@/lib/format';
import { colors, radius, spacing } from '@/lib/theme';

const TEN_THU = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

type ONgay = { ngayIso: string; soNgay: number } | null;

/**
 * Lịch tháng thu gọn: chấm nhỏ dưới ngày có buổi tập, bấm vào ngày để xem chi
 * tiết bên ngoài component này. Chấm màu vàng = ngày đó có buổi đang chờ
 * huấn luyện viên duyệt, chấm xanh = chỉ có buổi đã xử lý.
 */
export function LichThang({
  ngayCoDanhDau, ngayDuocChon, onChonNgay,
}: {
  /** key = "YYYY-MM-DD", value = ngày đó có buổi đang chờ duyệt hay không. */
  ngayCoDanhDau: Map<string, boolean>;
  ngayDuocChon: string | null;
  onChonNgay: (ngayIso: string) => void;
}) {
  const [thangXem, setThangXem] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });

  const homNayIso = useMemo(() => ngayIsoDiaPhuong(new Date()), []);

  const tuan = useMemo(() => {
    const nam = thangXem.getFullYear();
    const thang = thangXem.getMonth();
    const soNgayTrongThang = new Date(nam, thang + 1, 0).getDate();
    // Thứ của ngày 1 trong tháng, quy về Thứ 2 = 0 ... Chủ nhật = 6.
    const thuNgayDau = (new Date(nam, thang, 1).getDay() + 6) % 7;

    const o: ONgay[] = [];
    for (let i = 0; i < thuNgayDau; i++) o.push(null);
    for (let ngay = 1; ngay <= soNgayTrongThang; ngay++) {
      o.push({ ngayIso: ngayIsoDiaPhuong(new Date(nam, thang, ngay)), soNgay: ngay });
    }
    while (o.length % 7 !== 0) o.push(null);

    const hang: ONgay[][] = [];
    for (let i = 0; i < o.length; i += 7) hang.push(o.slice(i, i + 7));
    return hang;
  }, [thangXem]);

  return (
    <View style={styles.wrap}>
      <View style={styles.headerRow}>
        <Pressable
          hitSlop={8}
          onPress={() => setThangXem(new Date(thangXem.getFullYear(), thangXem.getMonth() - 1, 1))}
        >
          <Text style={styles.navArrow}>‹</Text>
        </Pressable>
        <Text style={styles.thangLabel}>Tháng {thangXem.getMonth() + 1}/{thangXem.getFullYear()}</Text>
        <Pressable
          hitSlop={8}
          onPress={() => setThangXem(new Date(thangXem.getFullYear(), thangXem.getMonth() + 1, 1))}
        >
          <Text style={styles.navArrow}>›</Text>
        </Pressable>
      </View>

      <View style={styles.rowThu}>
        {TEN_THU.map((t) => <Text key={t} style={styles.thuLabel}>{t}</Text>)}
      </View>

      {tuan.map((hang, i) => (
        <View key={i} style={styles.rowThu}>
          {hang.map((o, j) => {
            if (!o) return <View key={j} style={styles.o} />;
            const daChon = o.ngayIso === ngayDuocChon;
            const laHomNay = o.ngayIso === homNayIso;
            const choDuyet = ngayCoDanhDau.get(o.ngayIso);
            return (
              <Pressable key={j} style={styles.o} onPress={() => onChonNgay(o.ngayIso)}>
                <View style={[styles.oTron, daChon && styles.oTronChon]}>
                  <Text
                    style={[
                      styles.soNgay,
                      laHomNay && !daChon && styles.soNgayHomNay,
                      daChon && styles.soNgayChon,
                    ]}
                  >
                    {o.soNgay}
                  </Text>
                </View>
                <View
                  style={[
                    styles.cham,
                    choDuyet === true && styles.chamChoDuyet,
                    choDuyet === false && styles.chamBinhThuong,
                  ]}
                />
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.sm },
  headerRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: spacing.xs,
  },
  navArrow: { fontSize: 22, fontWeight: '700', color: colors.slate600, paddingHorizontal: spacing.md },
  thangLabel: { fontSize: 15, fontWeight: '700', color: colors.slate900 },
  rowThu: { flexDirection: 'row' },
  thuLabel: { flex: 1, textAlign: 'center', fontSize: 12, fontWeight: '600', color: colors.slate400 },
  o: { flex: 1, alignItems: 'center', paddingVertical: spacing.xs, gap: 3 },
  oTron: { width: 30, height: 30, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center' },
  oTronChon: { backgroundColor: colors.brand600 },
  soNgay: { fontSize: 14, color: colors.slate800 },
  soNgayHomNay: { color: colors.brand600, fontWeight: '700' },
  soNgayChon: { color: colors.white, fontWeight: '700' },
  cham: { width: 5, height: 5, borderRadius: radius.pill, backgroundColor: 'transparent' },
  chamBinhThuong: { backgroundColor: colors.brand500 },
  chamChoDuyet: { backgroundColor: colors.amber400 },
});
