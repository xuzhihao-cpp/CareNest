import type { MockServerPath } from '@/types/api';

export const mockServerPaths: MockServerPath[] = [
  {
    method: 'GET',
    url: '/api/v1/health',
    mockFile: 'mock/phase-01/health.json',
    responseShape: 'ApiResponse<HealthResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/version',
    mockFile: 'mock/phase-01/version.json',
    responseShape: 'ApiResponse<VersionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/auth/login',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<LoginResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/auth/logout',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<{}>'
  },
  {
    method: 'GET',
    url: '/api/v1/auth/me',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<AuthUser>'
  },
  {
    method: 'GET',
    url: '/api/v1/auth/menus',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<{menus: AuthMenu[]}>'
  },
  {
    method: 'GET',
    url: '/api/v1/auth/permissions',
    mockFile: 'mock/phase-03/permissions-fixtures.json',
    responseShape: 'ApiResponse<PermissionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/admin/roles/{roleId}/permissions',
    mockFile: 'mock/phase-03/permissions-fixtures.json',
    responseShape: 'ApiResponse<PermissionResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/elder/home-summary',
    mockFile: 'mock/home/elder-home-summary.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/family/home-summary',
    mockFile: 'mock/home/family-home-summary.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/nurse/workbench-summary',
    mockFile: 'mock/home/nurse-workbench-summary.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/admin/dashboard/overview',
    mockFile: 'mock/home/admin-dashboard-overview.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/bindings',
    mockFile: 'mock/phase-06/family-bindings.json',
    responseShape: 'ApiResponse<BindingResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/family/bindings',
    mockFile: 'mock/phase-06/family-bindings.json',
    responseShape: 'ApiResponse<BindingResponse[]>'
  },
  {
    method: 'GET',
    url: '/api/v1/elder/bindings',
    mockFile: 'mock/phase-06/family-bindings.json',
    responseShape: 'ApiResponse<BindingResponse[]>'
  },
  {
    method: 'POST',
    url: '/api/v1/elder/bindings/{bindingId}/approve',
    mockFile: 'mock/phase-06/family-bindings.json',
    responseShape: 'ApiResponse<BindingResponse>'
  },
  {
    method: 'PUT',
    url: '/api/v1/family/bindings/{bindingId}/scopes',
    mockFile: 'mock/phase-06/family-bindings.json',
    responseShape: 'ApiResponse<BindingResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/bindings/{bindingId}/revoke',
    mockFile: 'mock/phase-06/family-bindings.json',
    responseShape: 'ApiResponse<BindingResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/elders/{elderId}/profile',
    mockFile: 'mock/phase-07/family-elders.json',
    responseShape: 'ApiResponse<ElderProfileDetail>'
  },
  {
    method: 'PUT',
    url: '/api/v1/elders/{elderId}/profile',
    mockFile: 'mock/phase-07/family-elders.json',
    responseShape: 'ApiResponse<ElderProfileResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/family/elders',
    mockFile: 'mock/phase-07/family-elders.json',
    responseShape: 'ApiResponse<PageResult<ElderProfileDetail>>'
  },
  {
    method: 'GET',
    url: '/api/v1/service-items',
    mockFile: 'mock/phase-08/service-items.json',
    responseShape: 'ApiResponse<PageResult<ServiceItemResponse>>'
  },
  {
    method: 'GET',
    url: '/api/v1/service-items/{serviceId}',
    mockFile: 'mock/phase-08/service-items.json',
    responseShape: 'ApiResponse<ServiceItemResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/admin/service-items',
    mockFile: 'mock/phase-08/service-items.json',
    responseShape: 'ApiResponse<ServiceItemResponse>'
  },
  {
    method: 'PUT',
    url: '/api/v1/admin/service-items/{serviceId}',
    mockFile: 'mock/phase-08/service-items.json',
    responseShape: 'ApiResponse<ServiceItemResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/elders/{elderId}/service-addresses',
    mockFile: 'mock/phase-09/service-addresses.json',
    responseShape: 'ApiResponse<PageResult<ServiceAddressResponse>>'
  },
  {
    method: 'POST',
    url: '/api/v1/elders/{elderId}/service-addresses',
    mockFile: 'mock/phase-09/service-addresses.json',
    responseShape: 'ApiResponse<ServiceAddressResponse>'
  },
  {
    method: 'PUT',
    url: '/api/v1/service-addresses/{addressId}',
    mockFile: 'mock/phase-09/service-addresses.json',
    responseShape: 'ApiResponse<ServiceAddressResponse>'
  },
  {
    method: 'DELETE',
    url: '/api/v1/service-addresses/{addressId}',
    mockFile: 'mock/phase-09/service-addresses.json',
    responseShape: 'ApiResponse<ServiceAddressResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/orders',
    mockFile: 'mock/phase-10/family-orders.json',
    responseShape: 'ApiResponse<FamilyOrderResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/family/orders',
    mockFile: 'mock/phase-10/family-orders.json',
    responseShape: 'ApiResponse<PageResult<FamilyOrderResponse>>'
  },
  {
    method: 'GET',
    url: '/api/v1/orders/{orderId}',
    mockFile: 'mock/phase-10/family-orders.json',
    responseShape: 'ApiResponse<FamilyOrderResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/admin/orders',
    mockFile: 'mock/phase-11/admin-orders.json',
    responseShape: 'ApiResponse<PageResult<AdminOrderRecord>>'
  },
  {
    method: 'GET',
    url: '/api/v1/admin/orders/{orderId}',
    mockFile: 'mock/phase-11/admin-orders.json',
    responseShape: 'ApiResponse<PageResult<AdminOrderRecord>>'
  },
  {
    method: 'POST',
    url: '/api/v1/admin/orders/{orderId}/dispatch',
    mockFile: 'mock/phase-12/dispatch-response.json',
    responseShape: 'ApiResponse<TaskActionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/nurse/tasks/{taskId}/accept',
    mockFile: 'mock/phase-12/dispatch-response.json',
    responseShape: 'ApiResponse<TaskActionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/nurse/tasks/{taskId}/status',
    mockFile: 'mock/phase-12/dispatch-response.json',
    responseShape: 'ApiResponse<TaskActionResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/nurse/tasks',
    mockFile: 'mock/phase-13/nurse-tasks.json',
    responseShape: 'ApiResponse<PageResult<NurseTaskDetailRecord>>'
  },
  {
    method: 'GET',
    url: '/api/v1/nurse/tasks/{taskId}',
    mockFile: 'mock/phase-13/nurse-tasks.json',
    responseShape: 'ApiResponse<PageResult<NurseTaskDetailRecord>>'
  },
  {
    method: 'POST',
    url: '/api/v1/nurse/orders/{orderId}/service-records',
    mockFile: 'mock/phase-14/execution-response.json',
    responseShape: 'ApiResponse<CareExecutionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/nurse/orders/{orderId}/vital-signs',
    mockFile: 'mock/phase-14/execution-response.json',
    responseShape: 'ApiResponse<CareExecutionResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/orders/{orderId}/service-records',
    mockFile: 'mock/phase-14/service-records.json',
    responseShape: 'ApiResponse<PageResult<CareExecutionRecord>>'
  },
  {
    method: 'POST',
    url: '/api/v1/orders/{orderId}/service-report/generate',
    mockFile: 'mock/phase-15/service-report.json',
    responseShape: 'ApiResponse<ServiceReportResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/orders/{orderId}/service-report',
    mockFile: 'mock/phase-15/service-report.json',
    responseShape: 'ApiResponse<ServiceReportResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/elder/reports/{reportId}/ack',
    mockFile: 'mock/phase-16/report-ack.json',
    responseShape: 'ApiResponse<ReportAckResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/reports/{reportId}/ack',
    mockFile: 'mock/phase-16/report-ack.json',
    responseShape: 'ApiResponse<ReportAckResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/reports/{reportId}/archive-suggestions/decision',
    mockFile: 'mock/phase-16/report-ack.json',
    responseShape: 'ApiResponse<ReportAckResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/orders/{orderId}/cancel',
    mockFile: 'mock/phase-17/order-change.json',
    responseShape: 'ApiResponse<OrderChangeResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/family/orders/{orderId}/reschedule',
    mockFile: 'mock/phase-17/order-change.json',
    responseShape: 'ApiResponse<OrderChangeResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/admin/orders/{orderId}/cancel',
    mockFile: 'mock/phase-17/order-change.json',
    responseShape: 'ApiResponse<OrderChangeResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/admin/demo-data/status',
    mockFile: 'mock/phase-18/integration-status.json',
    responseShape: 'ApiResponse<StageEighteenStatusResponse>'
  }
];

