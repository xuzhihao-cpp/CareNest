import { request } from '@/api/client';
import type { HealthResponse, RouteEntry, VersionResponse } from '@/types/stageOne';

const routes: RouteEntry[] = [
  { roleCode: 'ELDER', routePath: '/pages/elder/index', appTitle: '长辈端', entryLabel: '进入长辈端', emptyStateTitle: '暂无待办', emptyStateDescription: '健康档案和服务提醒会显示在这里。' },
  { roleCode: 'FAMILY', routePath: '/pages/family/index', appTitle: '家属端', entryLabel: '进入家属端', emptyStateTitle: '暂无待办', emptyStateDescription: '绑定、预约和服务报告会显示在这里。' },
  { roleCode: 'NURSE', routePath: '/pages/nurse/index', appTitle: '护理端', entryLabel: '进入护理端', emptyStateTitle: '暂无任务', emptyStateDescription: '派发的护理任务会显示在这里。' },
  { roleCode: 'ADMIN', routePath: '/pages/admin/index', appTitle: '管理端', entryLabel: '进入管理端', emptyStateTitle: '暂无待处理事项', emptyStateDescription: '订单和平台运营数据会显示在这里。' },
  { roleCode: 'CUSTOMER_SERVICE', routePath: '/pages/admin/index?view=medical-files', appTitle: '客服审核端', entryLabel: '进入病历审核', emptyStateTitle: '暂无待审核资料', emptyStateDescription: '待处理病历资料会显示在这里。' }
];

export function getHealth() {
  return request<HealthResponse>({
    method: 'GET',
    url: '/health'
  });
}

export function getVersion() {
  return request<VersionResponse>({
    method: 'GET',
    url: '/version'
  });
}

export function getRoutes(): RouteEntry[] {
  return routes;
}
