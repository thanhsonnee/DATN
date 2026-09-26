import { StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';
import { colors, radius, spacing } from '@/lib/theme';

export function Input({
  label, hint, error, ...rest
}: { label?: string; hint?: string; error?: string } & TextInputProps) {
  return (
    <View style={{ gap: 4 }}>
      {label && <Text style={styles.label}>{label}</Text>}
      <TextInput
        placeholderTextColor={colors.slate400}
        style={[styles.input, error && styles.inputError]}
        {...rest}
      />
      {error ? (
        <Text style={styles.error}>{error}</Text>
      ) : hint ? (
        <Text style={styles.hint}>{hint}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  label: { fontSize: 13, fontWeight: '600', color: colors.slate700 },
  input: {
    borderWidth: 1,
    borderColor: colors.slate300,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 2,
    fontSize: 15,
    color: colors.slate900,
    backgroundColor: colors.white,
  },
  inputError: { borderColor: colors.red600 },
  hint: { fontSize: 12, color: colors.slate500 },
  error: { fontSize: 12, color: colors.red700 },
});
