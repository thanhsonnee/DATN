import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { Modal } from './Modal';
import { Button } from './Button';
import { colors, radius, spacing } from '@/lib/theme';

/**
 * Ô chọn ngày/giờ bằng picker gốc của hệ điều hành — thay cho gõ tay
 * "YYYY-MM-DD"/"HH:MM", đúng cảm giác như `<input type="date">` trên web.
 *
 * Android hiện dialog hệ thống qua API mệnh lệnh (`DateTimePickerAndroid.open`)
 * — không cần giữ component nào trong cây. iOS không có API mệnh lệnh tương
 * đương nên phải tự dựng bằng modal chứa picker dạng "spinner" + nút Xong.
 */
export function DateTimeField({
  label, mode, value, onChange, minimumDate, placeholder,
}: {
  label: string;
  mode: 'date' | 'time';
  value: Date | null;
  onChange: (d: Date) => void;
  minimumDate?: Date;
  placeholder?: string;
}) {
  const [iosOpen, setIosOpen] = useState(false);
  const [tam, setTam] = useState<Date>(value ?? new Date());

  const hienThi = value
    ? mode === 'date'
      ? value.toLocaleDateString('vi-VN')
      : value.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
    : (placeholder ?? (mode === 'date' ? 'Chọn ngày' : 'Chọn giờ'));

  const mo = () => {
    const goc = value ?? new Date();
    if (Platform.OS === 'android') {
      DateTimePickerAndroid.open({
        value: goc,
        mode,
        minimumDate,
        is24Hour: true,
        onChange: (event, date) => {
          if (event.type === 'set' && date) onChange(date);
        },
      });
    } else {
      setTam(goc);
      setIosOpen(true);
    }
  };

  return (
    <View style={{ gap: 4 }}>
      <Text style={styles.label}>{label}</Text>
      <Pressable style={styles.box} onPress={mo}>
        <Text style={value ? styles.value : styles.placeholder}>{hienThi}</Text>
      </Pressable>

      {Platform.OS === 'ios' && (
        <Modal open={iosOpen} title={label} onClose={() => setIosOpen(false)}>
          <DateTimePicker
            mode={mode}
            display="spinner"
            value={tam}
            minimumDate={minimumDate}
            is24Hour
            onChange={(_, date) => date && setTam(date)}
          />
          <Button onPress={() => { onChange(tam); setIosOpen(false); }}>Xong</Button>
        </Modal>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  label: { fontSize: 13, fontWeight: '600', color: colors.slate700 },
  box: {
    borderWidth: 1, borderColor: colors.slate300, borderRadius: radius.sm,
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm + 2, backgroundColor: colors.white,
  },
  value: { fontSize: 15, color: colors.slate900 },
  placeholder: { fontSize: 15, color: colors.slate400 },
});
