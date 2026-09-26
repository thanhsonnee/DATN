import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api, ApiError } from '@/api/client';
import { useAuth } from '@/stores/auth';
import { useRefetchOnFocus } from '@/hooks/useRefetchOnFocus';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Screen } from '@/components/ui/Screen';
import { tenVaiTro } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';

export function TaiKhoanScreen() {
  const user = useAuth((s) => s.user)!;
  const refreshUser = useAuth((s) => s.refreshUser);

  // isMember/memberCode có thể đổi do thao tác ở nơi khác (vd vừa được kích
  // hoạt gói ở quầy) — quay lại tab này phải thấy đúng, không cần đăng xuất/vào lại.
  useRefetchOnFocus(refreshUser);

  return (
    <Screen>
      <Text style={styles.h1}>Tài khoản</Text>

      <Card>
        <CardHeader title="Thông tin cá nhân" />
        <CardBody>
          <View style={styles.grid}>
            <Dong nhan="Họ và tên" giaTri={user.fullName} />
            <Dong nhan="Số điện thoại" giaTri={user.phone} />
            <Dong nhan="Vai trò" giaTri={tenVaiTro(user.role)} />
            <View style={styles.oItem}>
              <Text style={styles.oLabel}>Tình trạng hội viên</Text>
              {user.isMember ? (
                <Badge tone="green">{`Hội viên · ${user.memberCode}`}</Badge>
              ) : (
                <Badge tone="gray">Chưa mua gói</Badge>
              )}
            </View>
          </View>
        </CardBody>
      </Card>

      <DoiMatKhau />

      <Button variant="secondary" onPress={() => useAuth.getState().logout()}>Đăng xuất</Button>
    </Screen>
  );
}

function Dong({ nhan, giaTri }: { nhan: string; giaTri: string }) {
  return (
    <View style={styles.oItem}>
      <Text style={styles.oLabel}>{nhan}</Text>
      <Text style={styles.oValue}>{giaTri}</Text>
    </View>
  );
}

function DoiMatKhau() {
  const logout = useAuth((s) => s.logout);
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' });
  const [loi, setLoi] = useState('');
  const [xong, setXong] = useState(false);
  const [dangGui, setDangGui] = useState(false);

  const gui = async () => {
    setLoi('');
    setDangGui(true);
    try {
      await api.post('/auth/change-password', form);
      setXong(true);
      setForm({ currentPassword: '', newPassword: '' });
      setTimeout(logout, 2000);
    } catch (err) {
      setLoi(err instanceof ApiError ? err.message : 'Có lỗi xảy ra');
    } finally {
      setDangGui(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Đổi mật khẩu" />
      <CardBody>
        {xong ? (
          <Alert tone="success">Đổi mật khẩu thành công. Đang đưa bạn về màn đăng nhập…</Alert>
        ) : (
          <>
            <Alert tone="error">{loi}</Alert>
            <Input label="Mật khẩu hiện tại" secureTextEntry value={form.currentPassword}
                   onChangeText={(v) => setForm({ ...form, currentPassword: v })} />
            <Input label="Mật khẩu mới" secureTextEntry hint="Tối thiểu 8 ký tự" value={form.newPassword}
                   onChangeText={(v) => setForm({ ...form, newPassword: v })} />
            <Button onPress={gui} loading={dangGui}>Đổi mật khẩu</Button>
          </>
        )}
      </CardBody>
    </Card>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: 22, fontWeight: '800', color: colors.slate900 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md },
  oItem: { width: '45%', gap: 4 },
  oLabel: { fontSize: 11.5, color: colors.slate500 },
  oValue: { fontSize: 14, fontWeight: '600', color: colors.slate800 },
});
