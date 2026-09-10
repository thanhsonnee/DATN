import { useGuiYeuCauTuCheckIn } from '@/hooks/useCheckIn'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { Card, CardBody } from '@/components/ui/Card'
import { ApiError } from '@/api/client'

/**
 * Hội viên tự bấm khi đã có mặt tại phòng tập — thay cho việc lễ tân phải gõ
 * tìm tên. Chỉ đưa vào hàng đợi ở màn hình quầy, lễ tân vẫn là người nhìn ảnh
 * đối chiếu và xác nhận cuối cùng — không tự động mở cửa.
 */
export function TuCheckInPage() {
  const guiYeuCau = useGuiYeuCauTuCheckIn()

  return (
    <div className="mx-auto max-w-md space-y-6 text-center">
      <h1 className="text-2xl font-bold text-slate-900">Check-in phòng tập</h1>

      <Card>
        <CardBody className="space-y-4 py-10">
          {!guiYeuCau.isSuccess ? (
            <>
              <p className="text-sm text-slate-500">
                Đã có mặt tại phòng tập? Bấm nút bên dưới để báo cho lễ tân —
                lễ tân sẽ đối chiếu ảnh và xác nhận cho bạn vào tập.
              </p>
              <Button className="w-full py-4 text-base" loading={guiYeuCau.isPending}
                      onClick={() => guiYeuCau.mutate()}>
                📍 Tôi đã đến phòng tập
              </Button>
              {guiYeuCau.error instanceof ApiError && (
                <Alert tone="error">{guiYeuCau.error.message}</Alert>
              )}
            </>
          ) : (
            <>
              <div className={`rounded-lg px-4 py-3 text-sm font-semibold
                ${guiYeuCau.data.choPhepVao ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'}`}>
                {guiYeuCau.data.thongBao}
              </div>
              <p className="text-sm text-slate-500">
                Đã gửi yêu cầu tới quầy lễ tân. Vui lòng đợi lễ tân đối chiếu ảnh và xác nhận.
              </p>
              <Button variant="secondary" className="w-full" onClick={() => guiYeuCau.reset()}>
                Gửi lại yêu cầu
              </Button>
            </>
          )}
        </CardBody>
      </Card>
    </div>
  )
}
