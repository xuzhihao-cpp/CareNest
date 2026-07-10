const labels: Record<string, string> = {
  HEALTH_VIEW: '查看健康档案',
  HEALTH_EDIT: '编辑健康档案',
  ORDER_CREATE: '代为预约服务',
  REPORT_VIEW: '查看服务报告',
  REPORT_CONFIRM: '确认服务报告',
  ARCHIVE_EDIT: '编辑健康归档',
  PENDING: '待确认',
  ACTIVE: '已生效',
  REJECTED: '已拒绝',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
  ON_SHELF: '可预约',
  OFF_SHELF: '已下架',
  WAIT_DISPATCH: '待派单',
  DISPATCHED: '已派单',
  ACCEPTED: '已接单',
  ON_THE_WAY: '护理员已出发',
  SERVING: '服务中',
  WAIT_REPORT: '待生成报告',
  WAIT_CONFIRM: '待确认',
  COMPLETED: '已完成',
  CANCELED: '已取消',
  CANCELLED: '已取消',
  INIT: '初始状态',
  PUBLISHED: '已发布',
  DRAFT: '草稿',
  NORMAL: '正常',
  ABNORMAL: '异常',
  PASS: '通过',
  REJECT: '已拒绝',
  AGREE: '已同意',
  ELDER: '长辈',
  FAMILY: '家属',
  NURSE: '护理员',
  ADMIN: '管理员',
  CUSTOMER_SERVICE: '客服'
};

/** Converts backend enum codes to the wording users see. Unknown values remain visible. */
export function displayLabel(value: string | null | undefined): string {
  if (!value) {
    return '-';
  }
  return labels[value] ?? value;
}

export function displayScopeLabel(value: string | null | undefined): string {
  return displayLabel(value);
}
