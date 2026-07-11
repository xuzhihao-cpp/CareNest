<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getAdminOrders } from '@/api/stageEleven';
import { getOrderServiceRecords } from '@/api/stageFourteen';
import { generateServiceReport, getServiceReport } from '@/api/stageFifteen';
import { getUserReports } from '@/api/stageSixteen';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { AdminOrderRecord, AdminOrderStatus } from '@/types/stageEleven';
import type { ServiceReportResponse } from '@/types/stageFifteen';
import type { PendingReportRecord } from '@/types/stageSixteen';
import type { CareExecutionRecord } from '@/types/stageFourteen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const reportOrders = ref<AdminOrderRecord[]>([]);
const userReports = ref<PendingReportRecord[]>([]);
const selectedOrderId = ref('');
const report = ref<ServiceReportResponse | null>(null);
const reportSources = ref<CareExecutionRecord[]>([]);
const loading = ref(false);
const message = ref('');
const error = ref('');

const canGenerate = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const canViewOwnReports = computed(() => ['ELDER', 'FAMILY'].includes(props.roleCode));
const selectedOrder = computed(() => reportOrders.value.find((order) => order.orderId === selectedOrderId.value) ?? null);
const selectedUserReport = computed(() => userReports.value.find((item) => item.orderId === selectedOrderId.value) ?? null);
const reportReady = computed(() => Boolean(report.value?.reportId));

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function statusLabel(value: AdminOrderStatus) {
  const labels: Record<AdminOrderStatus, string> = {
    WAIT_DISPATCH: '待派单', DISPATCHED: '待接单', ACCEPTED: '已接单', ON_THE_WAY: '前往中',
    SERVING: '服务中', WAIT_REPORT: '待生成报告', WAIT_CONFIRM: '等待确认', COMPLETED: '已完成', CANCELED: '已取消'
  };
  return labels[value];
}

async function refreshOrders() {
  if (!canGenerate.value) return;
  loading.value = true;
  error.value = '';
  const response = await getAdminOrders({ page: 1, size: 100, orderStatus: '' });
  loading.value = false;
  if (response.code !== 0) {
    error.value = '服务订单加载失败，请刷新后重试。';
    return;
  }
  reportOrders.value = response.data.records.filter((order) => ['WAIT_REPORT', 'WAIT_CONFIRM', 'COMPLETED'].includes(order.orderStatus));
  if (!reportOrders.value.some((order) => order.orderId === selectedOrderId.value)) {
    selectedOrderId.value = reportOrders.value[0]?.orderId ?? '';
  }
  const selected = reportOrders.value.find((order) => order.orderId === selectedOrderId.value);
  if (selected?.orderStatus === 'WAIT_REPORT') {
    await loadReportSources(selected.orderId);
  }
}

async function refreshUserReports() {
  if (!canViewOwnReports.value) return;
  loading.value = true;
  error.value = '';
  const response = await getUserReports(props.roleCode as 'ELDER' | 'FAMILY');
  loading.value = false;
  if (response.code !== 0) {
    error.value = '服务报告加载失败，请刷新后重试。';
    return;
  }
  userReports.value = response.data;
  if (!userReports.value.some((item) => item.orderId === selectedOrderId.value)) {
    selectedOrderId.value = userReports.value[0]?.orderId ?? '';
  }
  if (selectedOrderId.value) {
    await loadUserReport();
  }
}

async function selectUserReport(item: PendingReportRecord) {
  selectedOrderId.value = item.orderId;
  report.value = null;
  await loadUserReport();
}

async function loadUserReport() {
  if (!selectedOrderId.value) return;
  loading.value = true;
  const response = await getServiceReport(selectedOrderId.value);
  loading.value = false;
  if (response.code !== 0) {
    report.value = null;
    error.value = '该服务报告暂时无法查看，请稍后重试。';
    return;
  }
  report.value = response.data;
}

async function selectOrder(order: AdminOrderRecord) {
  selectedOrderId.value = order.orderId;
  report.value = null;
  reportSources.value = [];
  message.value = '';
  error.value = '';
  if (order.orderStatus !== 'WAIT_REPORT') await loadReport(order);
  else await loadReportSources(order.orderId);
}

async function loadReportSources(orderId: string) {
  const response = await getOrderServiceRecords(orderId);
  if (response.code === 0) {
    reportSources.value = response.data.records;
  }
}

async function loadReport(order = selectedOrder.value) {
  if (!order) return;
  loading.value = true;
  const response = await getServiceReport(order.orderId);
  loading.value = false;
  if (response.code !== 0 || !response.data.reportId) {
    report.value = null;
    error.value = '该订单暂时没有可查看的服务报告。';
    return;
  }
  report.value = response.data;
  error.value = '';
}

async function handlePrimaryAction(order: AdminOrderRecord) {
  if (order.orderStatus !== 'WAIT_REPORT') {
    await loadReport(order);
    return;
  }
  if (reportSources.value.length === 0) {
    error.value = '请先由护理人员填写服务记录和护理建议，再生成报告。';
    return;
  }
  loading.value = true;
  error.value = '';
  const response = await generateServiceReport(order.orderId);
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.message;
    return;
  }
  report.value = response.data;
  message.value = '服务报告已生成，等待长辈或家属确认。';
  await refreshOrders();
}

onMounted(() => {
  if (canGenerate.value) return refreshOrders();
  return refreshUserReports();
});
</script>

<template>
  <view v-if="canGenerate" class="stage-fifteen-panel glass-panel" aria-label="服务报告">
    <view class="report-heading"><view><text class="report-page-title">服务报告</text><text class="report-subtitle">选择服务订单后，生成或查看对应报告。</text></view><button class="ghost-action" type="button" :disabled="loading" @click="refreshOrders">刷新</button></view>
    <view v-if="message" class="success-banner">{{ message }}</view>
    <view v-if="error" class="error-banner" role="alert">{{ error }}</view>

    <view class="report-picker-layout">
      <view class="report-order-list">
        <view class="report-list-heading"><text>服务订单</text><text>{{ reportOrders.length }} 笔</text></view>
        <view v-if="!loading && reportOrders.length === 0" class="report-empty">暂无可生成或查看报告的服务订单。</view>
        <button v-for="order in reportOrders" :key="order.orderId" class="report-order-card" :class="{ active: selectedOrderId === order.orderId }" type="button" @click="selectOrder(order)">
          <view><text class="flow-label">{{ order.serviceName || '上门护理服务' }}</text><text class="flow-time">服务联系人：{{ order.contactName || '长辈' }}</text><text class="flow-time">预约时间：{{ formatTime(order.scheduledStart) }}</text></view><text class="tag" :class="order.orderStatus === 'WAIT_REPORT' ? 'tag-amber' : 'tag-blue'">{{ statusLabel(order.orderStatus) }}</text>
          <text v-if="order.orderId === selectedOrderId && order.orderStatus === 'WAIT_REPORT' && reportSources.length === 0" class="tag tag-rose">未填写服务记录</text>
        </button>
      </view>

      <view class="report-preview">
        <view v-if="selectedOrder" class="selected-report-order"><text>{{ selectedOrder.serviceName || '上门护理服务' }}</text><text>服务联系人：{{ selectedOrder.contactName || '长辈' }}</text><text>预约时间：{{ formatTime(selectedOrder.scheduledStart) }}</text><button v-if="selectedOrder.orderStatus === 'WAIT_REPORT'" class="hero-action report-action" type="button" :disabled="loading" @click="handlePrimaryAction(selectedOrder)">生成报告</button></view>
        <view v-else class="report-empty">请从左侧选择一笔服务订单。</view>

        <view v-if="selectedOrder?.orderStatus === 'WAIT_REPORT'" class="report-source-preview">
          <text v-if="reportSources.length === 0" class="field-error">护理人员尚未填写服务记录和护理建议，请先在护理端完成本次服务记录。</text>
          <view class="report-source-heading"><text>将纳入报告的服务记录</text><text>{{ reportSources.length }} 条</text></view>
          <view v-if="reportSources.length === 0" class="report-empty">护理人员尚未填写服务记录，暂不能生成报告。</view>
          <view v-for="item in reportSources" :key="item.recordId" class="report-source-item">
            <text class="report-block-title">{{ formatTime(item.startTime) }} 至 {{ formatTime(item.endTime) }}</text>
            <text>{{ item.content }}</text>
            <text v-if="item.nursingAdvice">护理建议：{{ item.nursingAdvice }}</text>
            <text v-if="item.abnormalFlag" class="field-error">已标记异常情况</text>
          </view>
        </view>

        <view v-if="reportReady && report" class="report-content">
          <view class="report-block report-summary"><text class="report-block-title">服务总结</text><text>{{ report.summary }}</text></view>
          <view class="report-block"><text class="report-block-title">服务记录</text><text v-if="report.serviceRecords.length === 0">暂无服务记录。</text><text v-for="item in report.serviceRecords" :key="item">{{ item }}</text></view>
          <view class="report-block"><text class="report-block-title">生命体征</text><text v-if="report.vitalSigns.length === 0">暂无生命体征记录。</text><text v-for="item in report.vitalSigns" :key="item">{{ item }}</text></view>
          <view class="report-block"><text class="report-block-title">护理建议</text><text>{{ report.nursingAdvice || '暂无护理建议。' }}</text></view>
        </view>
        <view v-else-if="selectedOrder && selectedOrder.orderStatus === 'WAIT_REPORT'" class="report-empty">服务记录已完成，可生成服务报告。</view>
      </view>
    </view>
  </view>
  <view v-else-if="canViewOwnReports" class="stage-fifteen-panel glass-panel" aria-label="我的服务报告">
    <view class="report-heading">
      <view><text class="report-page-title">我的服务报告</text><text class="report-subtitle">查看已完成服务的记录、生命体征和护理建议。</text></view>
      <button class="ghost-action" type="button" :disabled="loading" @click="refreshUserReports">刷新</button>
    </view>
    <view v-if="error" class="error-banner" role="alert">{{ error }}</view>
    <view v-if="!loading && userReports.length === 0" class="report-empty">暂时没有可查看的服务报告。</view>
    <view v-else class="report-picker-layout">
      <view class="report-order-list">
        <button v-for="item in userReports" :key="item.reportId" class="report-order-card" :class="{ active: selectedOrderId === item.orderId }" type="button" @click="selectUserReport(item)">
          <view><text class="flow-label">{{ item.elderName }}的服务报告</text><text class="flow-time">点击查看本次服务详情</text></view><text class="tag tag-blue">服务报告</text>
        </button>
      </view>
      <view v-if="selectedUserReport && reportReady && report" class="report-content">
        <view class="report-block report-summary"><text class="report-block-title">服务总结</text><text>{{ report.summary || '本次服务已完成。' }}</text></view>
        <view class="report-block"><text class="report-block-title">服务记录</text><text v-if="report.serviceRecords.length === 0">暂无服务记录。</text><text v-for="item in report.serviceRecords" :key="item">{{ item }}</text></view>
        <view class="report-block"><text class="report-block-title">生命体征</text><text v-if="report.vitalSigns.length === 0">暂无生命体征记录。</text><text v-for="item in report.vitalSigns" :key="item">{{ item }}</text></view>
        <view class="report-block"><text class="report-block-title">护理建议</text><text>{{ report.nursingAdvice || '暂无护理建议。' }}</text></view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.stage-fifteen-panel { gap: 18px; padding: 22px; border-radius: 8px; }.report-heading, .report-list-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.report-page-title { display: block; color: #18312d; font-size: 21px; font-weight: 700; }.report-subtitle { display: block; margin-top: 5px; color: #6c7e79; font-size: 13px; }
.report-picker-layout { display: grid; gap: 18px; }.report-order-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 10px; min-width: 0; }.report-list-heading { grid-column: 1 / -1; color: #254740; font-size: 16px; font-weight: 700; }.report-list-heading text:last-child { color: #72837f; font-size: 13px; font-weight: 400; }.report-order-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: center; width: 100%; min-height: 90px; padding: 13px; border: 1px solid rgba(120, 144, 156, .16); border-radius: 8px; background: #fff; color: #40505d; text-align: left; }.report-order-card.active { border-color: rgba(11,143,157,.38); background: #f0faf7; }.report-order-card .flow-time { margin-top: 4px; }
.report-preview { display: grid; grid-template-columns: minmax(280px, .72fr) minmax(0, 1.28fr); gap: 14px; align-items: start; min-width: 0; }.report-content { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; min-width: 0; }.report-summary { grid-column: 1 / -1; }.selected-report-order, .report-block, .report-empty { display: grid; gap: 7px; box-sizing: border-box; width: 100%; padding: 14px; border: 1px solid #dce8e5; border-radius: 8px; background: #fff; color: #58706b; font-size: 14px; line-height: 1.55; }.selected-report-order { border-color: #c8dfda; background: #f3faf8; }.selected-report-order text:first-child { color: #1e4841; font-size: 17px; font-weight: 700; }.report-action { width: 100%; min-height: 42px; margin-top: 6px; border-radius: 6px; }.report-block { color: #405a55; }.report-block-title { color: #20433d; font-weight: 700; }.report-empty { min-height: 70px; align-content: center; color: #72837f; }
.report-source-preview { display: grid; gap: 8px; grid-column: 1 / -1; padding: 14px; border: 1px solid #cfe3de; border-radius: 8px; background: #f8fcfb; color: #47645e; }.report-source-heading { display: flex; justify-content: space-between; color: #20433d; font-weight: 700; }.report-source-heading text:last-child { color: #71837f; font-weight: 400; }.report-source-item { display: grid; gap: 4px; padding: 10px 0; border-top: 1px solid #deebe8; }.report-source-item:first-of-type { border-top: 0; }
.tag-rose { border-color: #f2b8b5; background: #fff1f0; color: #b43a36; }
@media (max-width: 900px) { .report-order-list { grid-template-columns: 1fr; }.report-preview, .report-content { grid-template-columns: 1fr; } }
</style>
