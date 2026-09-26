import { RefreshControl, ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, spacing } from '@/lib/theme';

/** Khung chung cho mọi màn hình: safe-area + cuộn được + kéo để làm mới (tùy chọn). */
export function Screen({
  children, scroll = true, onRefresh, refreshing, style,
}: {
  children: React.ReactNode;
  scroll?: boolean;
  onRefresh?: () => void;
  refreshing?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  if (!scroll) {
    return (
      <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>
        <View style={[styles.contentFixed, style]}>{children}</View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>
      <ScrollView
        contentContainerStyle={[styles.content, style]}
        refreshControl={
          onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} /> : undefined
        }
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.slate50 },
  // Dùng làm contentContainerStyle của ScrollView — KHÔNG cần flex: 1, ScrollView
  // tự co giãn theo nội dung.
  content: { padding: spacing.lg, gap: spacing.lg },
  // Dùng làm View thường (nhánh scroll=false) — CẦN flex: 1 để truyền không
  // gian xuống cho các View con muốn tự căn giữa (vd LoginScreen, CheckInScreen).
  // Thiếu dòng này thì các con có flex:1 bên trong co về cao 0, màn hình trắng trơn.
  contentFixed: { flex: 1, padding: spacing.lg, gap: spacing.lg },
});
