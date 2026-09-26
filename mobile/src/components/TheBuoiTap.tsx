import { StyleSheet, Text, View } from 'react-native';
import { Badge, type Tone } from '@/components/ui/Badge';
import { Card, CardBody } from '@/components/ui/Card';
import { ngayGio, tenLoaiBuoiTap, tenTrangThaiBuoi } from '@/lib/format';
import { colors, spacing } from '@/lib/theme';
import type { PtSession, SessionStatus } from '@/api/types';

function toneFor(status: SessionStatus): Tone {
  switch (status) {
    case 'COMPLETED': return 'green';
    case 'SCHEDULED': return 'blue';
    case 'PENDING_TRAINER': return 'amber';
    case 'REJECTED':
    case 'NO_SHOW':
    case 'CANCELLED': return 'red';
    default: return 'gray';
  }
}

/** Một buổi tập — dùng cho màn hình hội viên. */
export function TheBuoiTap({ buoi, doiTac, actions }: {
  buoi: PtSession;
  /** Tên bên còn lại — với hội viên thì đây là tên PT. */
  doiTac: string;
  actions?: React.ReactNode;
}) {
  return (
    <Card>
      <CardBody>
        <View style={styles.headRow}>
          <View style={{ flex: 1 }}>
            <Text style={styles.time}>{ngayGio(buoi.scheduledStart)}</Text>
            <Text style={styles.meta}>
              {doiTac} · {tenLoaiBuoiTap(buoi.sessionType)}{buoi.roomName ? ` · ${buoi.roomName}` : ''}
            </Text>
          </View>
          <Badge tone={toneFor(buoi.status)}>{tenTrangThaiBuoi(buoi.status)}</Badge>
        </View>

        {buoi.status === 'SCHEDULED' && (
          <View style={styles.confirmRow}>
            <TrangThaiXacNhan nhan="Huấn luyện viên" xong={!!buoi.trainerConfirmedAt} />
            <TrangThaiXacNhan nhan="Hội viên" xong={!!buoi.memberConfirmedAt} />
          </View>
        )}

        {buoi.status === 'COMPLETED' && buoi.autoConfirmed && (
          <Text style={styles.warnText}>Hệ thống tự duyệt do hội viên không phản hồi trong 24 giờ</Text>
        )}

        {buoi.rejectReason && <Text style={styles.errText}>Lý do từ chối: {buoi.rejectReason}</Text>}

        {buoi.cancelReason && (
          <Text style={styles.mutedText}>
            Hủy bởi {buoi.cancelledBy === 'MEMBER' ? 'hội viên' : 'huấn luyện viên'}
            {buoi.isLateCancel ? ' (hủy muộn, mất buổi)' : ''} — {buoi.cancelReason}
          </Text>
        )}

        {buoi.noShowBy && (
          <Text style={styles.errText}>
            Vắng mặt: {buoi.noShowBy === 'MEMBER' ? 'hội viên' : 'huấn luyện viên'}
            {buoi.noShowBy === 'TRAINER' ? ' — hội viên không bị mất buổi' : ''}
          </Text>
        )}

        {actions && <View style={styles.actionsRow}>{actions}</View>}
      </CardBody>
    </Card>
  );
}

function TrangThaiXacNhan({ nhan, xong }: { nhan: string; xong: boolean }) {
  return (
    <Text style={xong ? styles.confirmDone : styles.confirmPending}>
      {xong ? '✓' : '○'} {nhan} {xong ? 'đã xác nhận' : 'chưa xác nhận'}
    </Text>
  );
}

const styles = StyleSheet.create({
  headRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  time: { fontSize: 14.5, fontWeight: '600', color: colors.slate900 },
  meta: { fontSize: 12.5, color: colors.slate500, marginTop: 2 },
  confirmRow: {
    flexDirection: 'row', gap: spacing.lg, backgroundColor: colors.slate50,
    borderRadius: 8, paddingHorizontal: spacing.sm, paddingVertical: spacing.sm,
  },
  confirmDone: { fontSize: 12, fontWeight: '600', color: colors.emerald700 },
  confirmPending: { fontSize: 12, color: colors.slate400 },
  warnText: { fontSize: 12, color: colors.amber700 },
  errText: { fontSize: 13, color: colors.red700 },
  mutedText: { fontSize: 13, color: colors.slate600 },
  actionsRow: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-end', gap: spacing.sm },
});
