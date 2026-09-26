import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { LichDayScreen } from '@/screens/trainer/LichDayScreen';
import { DanhGiaCuaToiScreen } from '@/screens/trainer/DanhGiaCuaToiScreen';
import { TaiKhoanScreen } from '@/screens/member/TaiKhoanScreen';
import { colors } from '@/lib/theme';
import type { TrainerTabParamList } from './types';

const Tab = createBottomTabNavigator<TrainerTabParamList>();

const ICON: Record<keyof TrainerTabParamList, keyof typeof Ionicons.glyphMap> = {
  LichDay: 'calendar-outline',
  DanhGia: 'star-outline',
  TaiKhoan: 'person-outline',
};

const LABEL: Record<keyof TrainerTabParamList, string> = {
  LichDay: 'Lịch dạy',
  DanhGia: 'Đánh giá',
  TaiKhoan: 'Tài khoản',
};

export function TrainerTabs() {
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
      <Tab.Screen name="LichDay" component={LichDayScreen} />
      <Tab.Screen name="DanhGia" component={DanhGiaCuaToiScreen} />
      <Tab.Screen name="TaiKhoan" component={TaiKhoanScreen} />
    </Tab.Navigator>
  );
}
