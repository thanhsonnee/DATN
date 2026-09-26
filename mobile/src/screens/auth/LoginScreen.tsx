import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useAuth } from '@/stores/auth';
import { ApiError } from '@/api/client';
import { Card, CardBody } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Alert';
import { Screen } from '@/components/ui/Screen';
import { colors, spacing } from '@/lib/theme';
import type { AuthStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<AuthStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const login = useAuth((s) => s.login);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loi, setLoi] = useState('');
  const [dangGui, setDangGui] = useState(false);

  const guiForm = async () => {
    setLoi('');
    setDangGui(true);
    try {
      await login(username, password);
      // Không cần navigate thủ công — RootNavigator tự chuyển sang app chính khi `user` có giá trị.
    } catch (err) {
      setLoi(err instanceof ApiError ? err.message : 'Không kết nối được máy chủ');
    } finally {
      setDangGui(false);
    }
  };

  return (
    <Screen scroll={false}>
      <View style={styles.center}>
        <Card>
          <CardBody>
            <Text style={styles.title}>Đăng nhập</Text>
            <Text style={styles.subtitle}>Dùng số điện thoại đã đăng ký</Text>

            <Alert tone="error">{loi}</Alert>

            <Input
              label="Số điện thoại" autoComplete="tel" keyboardType="phone-pad"
              value={username} onChangeText={setUsername} placeholder="0912345678"
            />
            <Input
              label="Mật khẩu" secureTextEntry autoComplete="password"
              value={password} onChangeText={setPassword}
            />
            <Button onPress={guiForm} loading={dangGui} style={{ marginTop: spacing.sm }}>
              Đăng nhập
            </Button>

            <View style={styles.footerRow}>
              <Text style={styles.footerText}>Chưa có tài khoản? </Text>
              <Text style={styles.link} onPress={() => navigation.navigate('Register')}>
                Đăng ký ngay
              </Text>
            </View>
          </CardBody>
        </Card>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', padding: spacing.lg },
  title: { fontSize: 20, fontWeight: '700', color: colors.slate900 },
  subtitle: { fontSize: 13.5, color: colors.slate500, marginTop: 4, marginBottom: spacing.md },
  footerRow: { flexDirection: 'row', justifyContent: 'center', marginTop: spacing.md },
  footerText: { fontSize: 13.5, color: colors.slate500 },
  link: { fontSize: 13.5, color: colors.brand600, fontWeight: '600' },
});
