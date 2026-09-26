import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { GoiCuaToiScreen } from '@/screens/member/GoiCuaToiScreen';
import { CheckInScreen } from '@/screens/member/CheckInScreen';
import { BuoiTapScreen } from '@/screens/member/BuoiTapScreen';
import { PhanHoiScreen } from '@/screens/member/PhanHoiScreen';
import { TaiKhoanScreen } from '@/screens/member/TaiKhoanScreen';
import { colors } from '@/lib/theme';
import type { MemberTabParamList } from './types';

const Tab = createBottomTabNavigator<MemberTabParamList>();

const ICON: Record<keyof MemberTabParamList, keyof typeof Ionicons.glyphMap> = {
  GoiCuaToi: 'pricetag-outline',
  CheckIn: 'qr-code-outline',
  BuoiTap: 'barbell-outline',
  PhanHoi: 'chatbox-ellipses-outline',
  TaiKhoan: 'person-outline',
};

const LABEL: Record<keyof MemberTabParamList, string> = {
  GoiCuaToi: 'Gói tập',
  CheckIn: 'Check-in',
  BuoiTap: 'Buổi tập',
  PhanHoi: 'Phản hồi',
  TaiKhoan: 'Tài khoản',
};

export function MemberTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.brand600,
        tabBarInactiveTintColor: colors.slate400,
        tabBarLabel: LABEL[route.name],
        tabBarIcon: ({ color, size }) => <Ionicons name={ICON[route.name]} color={color} size={size} />,
      })}
    >
      <Tab.Screen name="GoiCuaToi" component={GoiCuaToiScreen} />
      <Tab.Screen name="CheckIn" component={CheckInScreen} />
      <Tab.Screen name="BuoiTap" component={BuoiTapScreen} />
      <Tab.Screen name="PhanHoi" component={PhanHoiScreen} />
      <Tab.Screen name="TaiKhoan" component={TaiKhoanScreen} />
    </Tab.Navigator>
  );
}
