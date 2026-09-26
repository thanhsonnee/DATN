import { useCallback, useRef } from 'react';
import { useFocusEffect } from '@react-navigation/native';

/**
 * Tự gọi lại `refetch` mỗi khi tab/màn hình này được focus (quay lại từ tab
 * khác) — để bắt kịp thay đổi từ nơi khác (web quầy, PT, admin...) trong lúc
 * người dùng đang ở tab khác, không cần tự kéo làm mới.
 *
 * Bỏ qua lần focus ĐẦU TIÊN (lúc mount) — `useQuery` đã tự fetch sẵn lúc đó
 * rồi, gọi thêm là thừa 1 request.
 */
export function useRefetchOnFocus(refetch: () => void) {
  const laLanDau = useRef(true);

  // Giữ bản mới nhất qua ref thay vì đưa `refetch` vào deps của useCallback:
  // nhiều màn hình truyền vào một arrow function tạo mới mỗi lần render, nếu
  // đưa vào deps thì effect chạy lại mỗi render → gọi refetch → re-render →
  // lặp vô hạn ("Maximum update depth exceeded").
  const refetchRef = useRef(refetch);
  refetchRef.current = refetch;

  useFocusEffect(
    useCallback(() => {
      if (laLanDau.current) {
        laLanDau.current = false;
        return;
      }
      refetchRef.current();
    }, []),
  );
}
