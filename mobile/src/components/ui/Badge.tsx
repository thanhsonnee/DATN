import { StyleSheet, Text, View } from 'react-native';
import { colors, radius } from '@/lib/theme';
import type { RegistrationStatus } from '@/api/types';

export type Tone = 'gray' | 'blue' | 'green' | 'amber' | 'red';

export function Badge({ tone = 'gray', children }: { tone?: Tone; children: React.ReactNode }) {
  return (
    <View style={[styles.base, toneStyles[tone]]}>
      <Text style={[styles.text, textToneStyles[tone]]}>{children}</Text>
    </View>
  );
}

export function tonesForRegistration(status: RegistrationStatus): Tone {
  switch (status) {
    case 'ACTIVE': return 'green';
    case 'PENDING_PAYMENT': return 'amber';
    case 'FROZEN': return 'blue';
    case 'COMPLETED': return 'gray';
    default: return 'red';
  }
}

const styles = StyleSheet.create({
  base: {
    alignSelf: 'flex-start',
    borderRadius: radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 3,
  },
  text: { fontSize: 11.5, fontWeight: '700' },
});

const toneStyles = StyleSheet.create({
  gray: { backgroundColor: colors.slate100 },
  blue: { backgroundColor: colors.brand50 },
  green: { backgroundColor: colors.emerald50 },
  amber: { backgroundColor: colors.amber50 },
  red: { backgroundColor: colors.red50 },
});

const textToneStyles = StyleSheet.create({
  gray: { color: colors.slate600 },
  blue: { color: colors.brand700 },
  green: { color: colors.emerald700 },
  amber: { color: colors.amber700 },
  red: { color: colors.red700 },
});
