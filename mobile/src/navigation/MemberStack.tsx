import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { MemberTabs } from './MemberTabs';
import { BangGiaScreen } from '@/screens/member/BangGiaScreen';
import { colors } from '@/lib/theme';
import type { MemberStackParamList } from './types';

const Stack = createNativeStackNavigator<MemberStackParamList>();

/**
 * Bọc MemberTabs trong 1 Stack để "push" được màn Bảng giá — thao tác hiếm
 * (mua/gia hạn gói), không đáng chiếm 1 tab cố định ở thanh dưới cùng.
 */
export function MemberStack() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="Tabs" component={MemberTabs} options={{ headerShown: false }} />
      <Stack.Screen
        name="BangGia"
        component={BangGiaScreen}
        options={{ title: 'Bảng giá', headerTintColor: colors.brand600 }}
      />
    </Stack.Navigator>
  );
}
