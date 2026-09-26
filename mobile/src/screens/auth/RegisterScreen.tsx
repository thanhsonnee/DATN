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

type Props = NativeStackScreenProps<AuthStackParamList, 'Register'>;

export function RegisterScreen({ navigation }: Props) {
  const register = useAuth((s) => s.register);

  const [form, setForm] = useState({ fullName: '', phone: '', email: '', password: '' });
  const [loi, setLoi] = useState('');
  const [loiTruong, setLoiTruong] = useState<Record<string, string>>({});
  const [dangGui, setDangGui] = useState(false);

  const doi = (ten: keyof typeof form) => (gt: string) => setForm({ ...form, [ten]: gt });

  const guiForm = async () => {
    setLoi('');
    setLoiTruong({});
    setDangGui(true);
    try {
      await register({ ...form, email: form.email || undefined });
    } catch (err) {
      if (err instanceof ApiError) {
        setLoiTruong(err.fields ?? {});
        if (!err.fields) setLoi(err.message);
      } else {
        setLoi('Không kết nối được máy chủ');
      }
    } finally {
      setDangGui(false);
    }
  };

  return (
    <Screen>
      <Card>
        <CardBody>
          <Text style={styles.title}>Tạo tài khoản</Text>
          <Text style={styles.subtitle}>
            Đăng ký miễn phí. Bạn chỉ trở thành hội viên khi mua gói đầu tiên.
          </Text>

          <Alert tone="error">{loi}</Alert>

          <Input label="Họ và tên" value={form.fullName} onChangeText={doi('fullName')}
                 error={loiTruong.fullName} placeholder="Nguyễn Văn An" />

          <Input label="Số điện thoại" value={form.phone} onChangeText={doi('phone')}
                 error={loiTruong.phone} placeholder="0912345678" keyboardType="phone-pad"
                 hint="Số điện thoại cũng là tên đăng nhập của bạn" />

          <Input label="Email (không bắt buộc)" value={form.email} onChangeText={doi('email')}
                 error={loiTruong.email} placeholder="an.nguyen@gmail.com" keyboardType="email-address"
                 autoCapitalize="none" hint="Dùng để lấy lại mật khẩu khi quên" />

          <Input label="Mật khẩu" value={form.password} onChangeText={doi('password')}
                 error={loiTruong.password} secureTextEntry hint="Tối thiểu 8 ký tự" />

          <Button onPress={guiForm} loading={dangGui} style={{ marginTop: spacing.sm }}>
            Đăng ký
          </Button>

          <View style={styles.footerRow}>
            <Text style={styles.footerText}>Đã có tài khoản? </Text>
            <Text style={styles.link} onPress={() => navigation.navigate('Login')}>
              Đăng nhập
            </Text>
          </View>
        </CardBody>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 20, fontWeight: '700', color: colors.slate900 },
  subtitle: { fontSize: 13.5, color: colors.slate500, marginTop: 4, marginBottom: spacing.md },
  footerRow: { flexDirection: 'row', justifyContent: 'center', marginTop: spacing.md },
  footerText: { fontSize: 13.5, color: colors.slate500 },
  link: { fontSize: 13.5, color: colors.brand600, fontWeight: '600' },
});
