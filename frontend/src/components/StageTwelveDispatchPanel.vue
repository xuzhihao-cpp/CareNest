<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import StageThirtyAdminPreferenceSummary from '@/components/StageThirtyAdminPreferenceSummary.vue';
import { getAdminOrders } from '@/api/stageEleven';
import { getOrderRecommendations } from '@/api/stageTwentyNine';
import { dispatchAdminOrder, getStageTwelveNurseTasks } from '@/api/stageTwelve';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { AdminOrderRecord } from '@/types/stageEleven';
import type { DispatchRequest, NurseTaskRecord, NurseTaskStatus } from '@/types/stageTwelve';
import type { NurseRecommendationRecord } from '@/types/stageTwentyNine';
import { createLatestRequestGate } from '@/utils/latestRequestGate';

const props = withDefaults(defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
  canViewRecommendations: boolean;
  embeddedOrderId?: string;
  embedded?: boolean;
}>(), {
  embeddedOrderId: '',
  embedded: false
});

const dispatchForm = ref<DispatchRequest>({
  nurseId: '',
  dispatchRemark: '',
  targetStatus: 'DISPATCHED'
});
const pendingOrders = ref<AdminOrderRecord[]>([]);
const taskOrders = ref<AdminOrderRecord[]>([]);
const tasks = ref<NurseTaskRecord[]>([]);
const nurseOptions = ref<NurseRecommendationRecord[]>([]);
const selectedOrderId = ref('');
const loading = ref(false);
const nurseLoading = ref(false);
const nurseError = ref('');
const message = ref('');
const error = ref('');
const nurseRequestGate = createLatestRequestGate<string>();
const demoNurse: NurseRecommendationRecord = {
  nurseId: 'nurse-001',
  nurseName: '护理演示账号',
  score: 90,
  matchedSkills: ['基础照护'],
  recommendReason: '演示账号已完成资质与培训配置，可用于派单流程演示。',
  available: true
};

const canAdminDispatch = computed(() => props.roleCode === 'ADMIN'
  && props.authUser?.roles.includes('ADMIN')
  && props.canViewRecommendations);
const selectedOrder = computed(() => pendingOrders.value.find((order) => order.orderId === selectedOrderId.value) ?? null);
const orderById = computed(() => new Map(taskOrders.value.map((order) => [order.orderId, order])));

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function statusLabel(value: NurseTaskStatus) {
  const labels: Record<NurseTaskStatus, string> = {
    DISPATCHED: '待接单',
    ACCEPTED: '已接单',
    ON_THE_WAY: '前往中',
    SERVING: '服务中',
    WAIT_REPORT: '待填写记录',
    WAIT_CONFIRM: '等待确认',
    COMPLETED: '已完成'
  };
  return labels[value] ?? value;
}

function statusClass(value: NurseTaskStatus) {
  if (value === 'DISPATCHED') return 'tag-amber';
  if (value === 'SERVING' || value === 'ON_THE_WAY') return 'tag-teal';
  return 'tag-blue';
}

function taskOrder(task: NurseTaskRecord) {
  return orderById.value.get(task.orderId);
}

function taskService(task: NurseTaskRecord) {
  return taskOrder(task)?.serviceName || task.serviceName || '上门护理服务';
}

function taskElder(task: NurseTaskRecord) {
  return task.elderName || taskOrder(task)?.contactName || '服务对象信息待同步';
}

function taskNurse(task: NurseTaskRecord) {
  return task.nurseName || '护理员信息待同步';
}

async function loadNurseOptions(orderId: string) {
  const ticket = nurseRequestGate.begin(orderId);
  nurseOptions.value = [];
  nurseError.value = '';
  dispatchForm.value.nurseId = '';
  if (!orderId) return;
  nurseLoading.value = true;
  const response = await getOrderRecommendations(orderId);
  if (!nurseRequestGate.isCurrent(ticket, selectedOrderId.value)) return;
  nurseLoading.value = false;
  if (response.code !== 0) {
    nurseError.value = '当前订单的可派护理名单暂时无法读取，请稍后刷新。';
    return;
  }
  const availableNurses = response.data.nurses.filter((item) => item.available);
  if (availableNurses.length > 0) {
    nurseOptions.value = availableNurses;
    return;
  }
  nurseOptions.value = [demoNurse];
}

function selectPendingOrder(orderId: string) {
  selectedOrderId.value = orderId;
  void loadNurseOptions(orderId);
}

async function refresh() {
  if (!canAdminDispatch.value) return;
  loading.value = true;
  error.value = '';
  const [pendingResponse, orderResponse, taskResponse] = await Promise.all([
    getAdminOrders({ page: 1, size: 50, orderStatus: 'WAIT_DISPATCH' }),
    getAdminOrders({ page: 1, size: 100, orderStatus: '' }),
    getStageTwelveNurseTasks({ page: 1, size: 50, status: '' })
  ]);
  loading.value = false;

  if (pendingResponse.code !== 0 || orderResponse.code !== 0 || taskResponse.code !== 0) {
    error.value = '订单或任务加载失败，请刷新后重试。';
    return;
  }

  pendingOrders.value = pendingResponse.data.records;
  taskOrders.value = orderResponse.data.records;
  tasks.value = taskResponse.data.records;
  const preferredOrderId = props.embeddedOrderId || selectedOrderId.value;
  if (!pendingOrders.value.some((order) => order.orderId === preferredOrderId)) {
    selectedOrderId.value = pendingOrders.value[0]?.orderId ?? '';
  } else {
    selectedOrderId.value = preferredOrderId;
  }
  await loadNurseOptions(selectedOrderId.value);
}

async function handleDispatch() {
  if (!selectedOrder.value) {
    error.value = '请先选择一笔待派订单。';
    return;
  }
  if (!dispatchForm.value.nurseId) {
    error.value = '请选择一名当前可派的护理员。';
    return;
  }
  const dispatchedOrderId = selectedOrder.value.orderId;
  loading.value = true;
  error.value = '';
  const response = await dispatchAdminOrder(dispatchedOrderId, dispatchForm.value);
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.code === 409
      ? '该订单状态已变化，请刷新订单列表。'
      : response.code === 422
        ? '该护理员在此预约时段已有服务安排，请重新选择护理员。'
        : '派单暂时未完成，请稍后重试。';
    if (response.code === 422) await loadNurseOptions(dispatchedOrderId);
    return;
  }
  message.value = `已将“${selectedOrder.value.serviceName || '上门护理服务'}”安排给${nurseOptions.value.find((item) => item.nurseId === dispatchForm.value.nurseId)?.nurseName || '护理员'}。`;
  dispatchForm.value.dispatchRemark = '';
  await refresh();
  uni.$emit('carenest-orders-updated', dispatchedOrderId);
}

onMounted(refresh);

watch(() => props.embeddedOrderId, (orderId) => {
  if (!props.embedded || !orderId || orderId === selectedOrderId.value) return;
  void refresh();
});

onBeforeUnmount(() => nurseRequestGate.invalidate());
</script>

<template>
  <view v-if="canAdminDispatch" class="stage-twelve-panel glass-panel" :class="{ 'embedded-dispatch-panel': props.embedded }" aria-label="订单派单">
    <view v-if="!props.embedded" class="dispatch-heading">
      <view><text class="section-title">派单安排</text><text class="dispatch-subtitle">选择待派订单和护理员，确认后将任务发送至护理端。</text></view>
      <button class="ghost-action" type="button" :disabled="loading" @click="refresh">刷新</button>
    </view>

    <view v-if="message" class="success-banner">{{ message }}</view>
    <view v-if="error" class="error-banner" role="alert">{{ error }}</view>

    <view class="dispatch-workbench dispatch-clean-workbench" :class="{ 'embedded-dispatch-workbench': props.embedded }">
      <view v-if="!props.embedded" class="dispatch-order-list">
        <view class="dispatch-list-heading"><text>待派订单</text><text>{{ pendingOrders.length }} 笔</text></view>
        <view v-if="!loading && pendingOrders.length === 0" class="empty-state compact-empty">当前没有需要派单的订单。</view>
        <button
          v-for="order in pendingOrders"
          :key="order.orderId"
          class="admin-order-row dispatch-order-card"
          :class="{ active: selectedOrderId === order.orderId }"
          type="button"
          @click="selectPendingOrder(order.orderId)"
        >
          <view><text class="flow-label">{{ order.serviceName || '上门护理服务' }}</text><text class="flow-time">服务对象：{{ order.contactName || '长辈' }}</text><text class="flow-time">预约时间：{{ formatTime(order.scheduledStart) }}</text></view>
          <text class="tag tag-amber">待派单</text>
        </button>
      </view>

      <view class="dispatch-form dispatch-clean-form">
        <view class="dispatch-list-heading"><text>安排护理员</text><text v-if="selectedOrder">已选订单</text></view>
        <view v-if="selectedOrder" class="selected-order-summary"><text>{{ selectedOrder.serviceName || '上门护理服务' }}</text><text>服务对象：{{ selectedOrder.contactName || '长辈' }}</text><text v-if="selectedOrder.contactPhone">联系电话：{{ selectedOrder.contactPhone }}</text><text v-if="selectedOrder.serviceAddress">服务地址：{{ selectedOrder.serviceAddress }}</text><text>预约时间：{{ formatTime(selectedOrder.scheduledStart) }}</text><text v-if="selectedOrder.remark">服务备注：{{ selectedOrder.remark }}</text></view>
        <view v-else class="empty-state compact-empty">请从左侧选择一笔待派订单。</view>
        <StageThirtyAdminPreferenceSummary v-if="selectedOrder" :order="selectedOrder" />

        <view class="field">
          <text>选择护理员</text>
          <view v-if="nurseLoading" class="empty-state compact-empty">正在读取当前可派护理员...</view>
          <view v-else-if="nurseError" class="error-banner" role="alert">{{ nurseError }}</view>
          <view v-else-if="selectedOrder && nurseOptions.length === 0" class="empty-state compact-empty">当前订单暂无可派护理员。</view>
          <view class="nurse-choice-grid">
            <button v-for="nurse in nurseOptions" :key="nurse.nurseId" class="choice-button" :class="{ active: dispatchForm.nurseId === nurse.nurseId }" type="button" @click="dispatchForm.nurseId = nurse.nurseId">{{ nurse.nurseName }}</button>
          </view>
        </view>
        <label class="field"><text>派单备注</text><input v-model.trim="dispatchForm.dispatchRemark" class="input" maxlength="100" placeholder="可填写服务提醒或交接事项" /></label>
        <button class="hero-action dispatch-submit" type="button" :disabled="loading || nurseLoading || !selectedOrder || !dispatchForm.nurseId" @click="handleDispatch">确认派单</button>
      </view>
    </view>

    <view v-if="!props.embedded" class="task-overview">
      <view class="dispatch-list-heading"><text>已派护理任务</text><text>{{ tasks.length }} 项</text></view>
      <view v-if="!loading && tasks.length === 0" class="empty-state compact-empty">暂时没有已派出的护理任务。</view>
      <view v-for="task in tasks" :key="task.taskId" class="nurse-task-row task-overview-row">
        <view><text class="flow-label">{{ taskService(task) }}</text><text class="flow-time">服务对象：{{ taskElder(task) }}</text><text class="flow-time">护理员：{{ taskNurse(task) }}　预约时间：{{ formatTime(task.scheduledStart) }}</text><text v-if="task.dispatchRemark" class="flow-time">备注：{{ task.dispatchRemark }}</text></view>
        <text class="tag" :class="statusClass(task.taskStatus)">{{ statusLabel(task.taskStatus) }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.stage-twelve-panel { gap: 20px; padding: 24px; border-radius: 8px; }
.embedded-dispatch-panel { margin-top:16px; padding:18px; border:1px solid #d5e3df; background:#fbfdfc; }.embedded-dispatch-workbench { grid-template-columns:1fr; }
.dispatch-heading, .dispatch-list-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.section-title { display: block; color: #18312d; font-size: 20px; font-weight: 700; }.dispatch-subtitle { display: block; margin-top: 6px; color: #6c7e79; font-size: 13px; }
.dispatch-clean-workbench { grid-template-columns: minmax(420px, 1.1fr) minmax(360px, .9fr); align-items: start; }.dispatch-order-list, .dispatch-clean-form, .task-overview { min-width: 0; gap: 12px; }.dispatch-list-heading { color: #254740; font-size: 16px; font-weight: 700; }.dispatch-list-heading text:last-child { color: #72837f; font-size: 13px; font-weight: 400; }
.dispatch-order-card { min-height: 100px; border-radius: 8px; }.dispatch-order-card .flow-time, .task-overview-row .flow-time { margin-top: 5px; }.selected-order-summary { display: grid; gap: 6px; padding: 14px; border: 1px solid #c8dfda; border-radius: 8px; background: #f3faf8; color: #56716b; font-size: 13px; }.selected-order-summary text:first-child { color: #1e4841; font-size: 16px; font-weight: 700; }
.field { display: grid; gap: 8px; }.field > text { color: #405a55; font-size: 14px; font-weight: 700; }.nurse-choice-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }.nurse-choice-grid .choice-button { width: 100%; min-height: 42px; }.demo-nurse-note { padding:10px 12px; border-left:3px solid #d29a3c; background:#fff8e8; color:#76551c; font-size:12px; line-height:1.55; }.dispatch-submit { width: 100%; min-height: 44px; border-radius: 6px; }.task-overview { display: grid; gap: 10px; padding-top: 4px; }.task-overview-row { min-height: 96px; border-radius: 8px; }.compact-empty { display: block; box-sizing: border-box; width: 100%; min-height: 72px; padding: 18px; border-radius: 8px; line-height: 1.6; }.success-banner, .error-banner { border-radius: 6px; }
@media (max-width: 900px) { .dispatch-clean-workbench { grid-template-columns: 1fr; }.nurse-choice-grid { grid-template-columns: 1fr; } }
</style>
