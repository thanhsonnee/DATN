import { ActivityIndicator, Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from 'react-native';
import { colors, radius, spacing } from '@/lib/theme';

type Variant = 'primary' | 'secondary' | 'danger';

export function Button({
  children, onPress, loading, disabled, variant = 'primary', style,
}: {
  children: string;
  onPress?: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: Variant;
  style?: StyleProp<ViewStyle>;
}) {
  const tatDi = disabled || loading;

  return (
    <Pressable
      onPress={tatDi ? undefined : onPress}
      style={({ pressed }) => [
        styles.base,
        variantStyles[variant],
        tatDi && styles.disabled,
        pressed && !tatDi && styles.pressed,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={variant === 'secondary' ? colors.slate700 : colors.white} />
      ) : (
        <Text style={[styles.text, textVariantStyles[variant]]}>{children}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  disabled: { opacity: 0.5 },
  pressed: { opacity: 0.85 },
  text: { fontSize: 15, fontWeight: '600' },
});

const variantStyles = StyleSheet.create({
  primary: { backgroundColor: colors.brand600 },
  secondary: { backgroundColor: colors.slate100, borderWidth: 1, borderColor: colors.slate200 },
  danger: { backgroundColor: colors.red600 },
});

const textVariantStyles = StyleSheet.create({
  primary: { color: colors.white },
  secondary: { color: colors.slate700 },
  danger: { color: colors.white },
});
