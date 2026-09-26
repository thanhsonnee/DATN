import { StyleSheet, Text, View } from 'react-native';
import { colors, radius, spacing } from '@/lib/theme';

type Tone = 'info' | 'success' | 'error';

export function Alert({ tone = 'info', children }: { tone?: Tone; children: React.ReactNode }) {
  if (!children) return null;
  return (
    <View style={[styles.base, toneStyles[tone]]}>
      <Text style={[styles.text, toneTextStyles[tone]]}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  base: { borderRadius: radius.sm, padding: spacing.md },
  text: { fontSize: 13.5, lineHeight: 19 },
});

const toneStyles = StyleSheet.create({
  info: { backgroundColor: colors.brand50 },
  success: { backgroundColor: colors.emerald50 },
  error: { backgroundColor: colors.red50 },
});

const toneTextStyles = StyleSheet.create({
  info: { color: colors.brand700 },
  success: { color: colors.emerald800 },
  error: { color: colors.red700 },
});
