import { StyleSheet, Text, View } from 'react-native';
import { useAuth } from '@/stores/auth';
import { AuthNavigator } from './AuthNavigator';
import { MemberStack } from './MemberStack';
import { TrainerTabs } from './TrainerTabs';
import { Spinner } from '@/components/ui/Spinner';
import { Button } from '@/components/ui/Button';
import { colors, spacing } from '@/lib/theme';

export function RootNavigator() {
  const { user, loading } = useAuth();

  if (loading) return <Spinner label="Đang khôi phục phiên đăng nhập…" />;

  if (!user) return <AuthNavigator />;

  if (user.role === 'MEMBER') return <MemberStack />;
  if (user.role === 'TRAINER') return <TrainerTabs />;

  // Sale/Lễ tân/Kế toán/Admin dùng bản web — app mobile chỉ có Hội viên + PT.
  return <ChuaHoTroRole role={user.role} />;
}

function ChuaHoTroRole({ role }: { role: string }) {
  return (
    <View style={styles.wrap}>
      <Text style={styles.title}>Vai trò "{role}" chưa được hỗ trợ trên app</Text>
      <Text style={styles.text}>
        App di động hiện chỉ dành cho Hội viên. Vui lòng dùng bản web để thao tác với vai trò này.
      </Text>
      <Button variant="secondary" onPress={() => useAuth.getState().logout()}>Đăng xuất</Button>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.md },
  title: { fontSize: 16, fontWeight: '700', color: colors.slate900, textAlign: 'center' },
  text: { fontSize: 13.5, color: colors.slate500, textAlign: 'center' },
});
