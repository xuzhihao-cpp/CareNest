<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { uploadMedicalFileAsset } from '@/api/stageTwenty';
import { getFamilyOrders } from '@/api/stageTen';
import { submitFamilyComplaint, submitFamilyReview } from '@/api/stageFortyFourToFortyEight';
import type { FamilyOrderResponse } from '@/types/stageTen';

type Mode = 'REVIEW' | 'COMPLAINT';
const orders = ref<FamilyOrderResponse[]>([]);
const selectedOrderId = ref('');
const mode = ref<Mode>('REVIEW');
const rating = ref(5);
const tags = ref<string[]>([]);
const content = ref('');
const reasonType = ref('SERVICE_QUALITY');
const selectedFile = ref<{ path: string; name: string; size: number } | null>(null);
const uploading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const loading = ref(false);

const tagOptions = ['服务专业', '沟通耐心', '准时到达', '记录清楚'];
const eligibleOrders = computed(() => orders.value.filter((item) =>
  item.orderStatus === 'WAIT_CONFIRM' || item.orderStatus === 'COMPLETED'
));
const selectedOrder = computed(() => eligibleOrders.value.find((item) => item.orderId === selectedOrderId.value));

function time(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function toggleTag(value: string) {
  tags.value = tags.value.includes(value)
    ? tags.value.filter((item) => item !== value)
    : [...tags.value, value];
}

async function loadOrders() {
  loading.value = true;
  error.value = '';
  const response = await getFamilyOrders();
  loading.value = false;
  if (response.code !== 0) {
    orders.value = [];
    error.value = response.code === 403
      ? '当前绑定没有查看服务订单的权限。'
      : '服务订单暂时无法读取，请稍后重试。';
    return;
  }
  orders.value = response.data.records;
  if (!eligibleOrders.value.some((item) => item.orderId === selectedOrderId.value)) {
    selectedOrderId.value = eligibleOrders.value[0]?.orderId || '';
  }
}

function chooseAttachment() {
  if (submitting.value || uploading.value) return;
  uni.chooseFile({
    count: 1,
    type: 'all',
    extension: ['pdf', 'jpg', 'jpeg', 'png'],
    success(result) {
      const files = (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) as Array<File | {
        path: string; name?: string; size: number; type?: string;
      }>;
      const file = files[0];
      if (!file) return;
      const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths];
      const value = file as { path?: string; name?: string; size: number; type?: string };
      const path = paths[0] || value.path || '';
      const name = value.name || path.split('/').pop() || '附件';
      const extension = name.split('.').pop()?.toLowerCase();
      if (!['pdf', 'jpg', 'jpeg', 'png'].includes(extension || '') || value.size > 10 * 1024 * 1024) {
        error.value = '附件仅支持 PDF、JPG、PNG，且不能超过 10 MB。';
        return;
      }
      selectedFile.value = { path, name, size: value.size };
      error.value = '';
    }
  });
}

async function uploadAttachment() {
  if (!selectedFile.value) return [] as string[];
  uploading.value = true;
  const response = await uploadMedicalFileAsset(selectedFile.value.path, () => undefined);
  uploading.value = false;
  if (response.code !== 0 || !response.data.fileId?.trim()) {
    throw new Error('附件上传失败，请检查网络后重试。');
  }
  return [response.data.fileId.trim()];
}

async function submit() {
  if (!selectedOrder.value || submitting.value) return;
  if (!content.value.trim()) {
    error.value = mode.value === 'REVIEW' ? '请填写本次服务感受。' : '请说明需要处理的问题。';
    return;
  }
  if (content.value.trim().length > 500) {
    error.value = '说明不能超过 500 个字。';
    return;
  }
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const fileIds = await uploadAttachment();
    const response = mode.value === 'REVIEW'
      ? await submitFamilyReview(selectedOrder.value.orderId, {
          rating: rating.value, tags: tags.value, content: content.value.trim(), reasonType: null, fileIds
        })
      : await submitFamilyComplaint(selectedOrder.value.orderId, {
          rating: null, tags: [], content: content.value.trim(), reasonType: reasonType.value, fileIds
        });
    if (response.code !== 0) {
      if (response.code === 403) error.value = '当前绑定没有提交评价或投诉的权限。';
      else if (response.code === 409) error.value = '该订单当前不能重复评价，或状态已经变化，请刷新后重试。';
      else error.value = '提交未完成，请检查填写内容后重试。';
      return;
    }
    notice.value = mode.value === 'REVIEW' ? '服务评价已提交，感谢您的反馈。' : '投诉已提交，客服人员将跟进处理。';
    content.value = '';
    tags.value = [];
    selectedFile.value = null;
    await loadOrders();
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '提交未完成，请稍后重试。';
  } finally {
    submitting.value = false;
  }
}

onMounted(loadOrders);
</script>

<template>
  <view class="feedback-panel">
    <view class="panel-heading"><view><text>服务评价与投诉</text><small>从已完成服务中选择，无需填写订单编号</small></view><button type="button" :disabled="loading" @click="loadOrders">刷新</button></view>
    <view v-if="notice" class="notice success">{{ notice }}</view>
    <view v-if="error" class="notice error">{{ error }}</view>
    <view v-if="!loading && eligibleOrders.length === 0" class="empty">暂无可评价的已完成服务。</view>
    <view v-else class="order-list">
      <button v-for="order in eligibleOrders" :key="order.orderId" type="button" :class="{ selected: selectedOrderId === order.orderId }" @click="selectedOrderId = order.orderId">
        <strong>{{ order.serviceName || '上门护理服务' }}</strong>
        <text>{{ time(order.scheduledStart) }}</text>
      </button>
    </view>
    <template v-if="selectedOrder">
      <view class="mode-tabs"><button type="button" :class="{ active: mode === 'REVIEW' }" @click="mode='REVIEW'">服务评价</button><button type="button" :class="{ active: mode === 'COMPLAINT' }" @click="mode='COMPLAINT'">问题投诉</button></view>
      <view v-if="mode === 'REVIEW'" class="form-section">
        <text class="label">满意度</text>
        <view class="rating"><button v-for="value in 5" :key="value" type="button" :class="{ active: rating === value }" @click="rating=value">{{ value }} 分</button></view>
        <text class="label">服务印象</text>
        <view class="tags"><button v-for="tag in tagOptions" :key="tag" type="button" :class="{ active: tags.includes(tag) }" @click="toggleTag(tag)">{{ tag }}</button></view>
      </view>
      <view v-else class="form-section">
        <text class="label">问题类型</text>
        <picker :range="['服务质量', '服务态度', '时间安排', '收费问题', '其他问题']" :value="['SERVICE_QUALITY','SERVICE_ATTITUDE','SCHEDULE','CHARGE','OTHER'].indexOf(reasonType)" @change="reasonType=['SERVICE_QUALITY','SERVICE_ATTITUDE','SCHEDULE','CHARGE','OTHER'][Number($event.detail.value)]"><view class="picker-value">{{ {SERVICE_QUALITY:'服务质量',SERVICE_ATTITUDE:'服务态度',SCHEDULE:'时间安排',CHARGE:'收费问题',OTHER:'其他问题'}[reasonType] }}</view></picker>
      </view>
      <text class="label">{{ mode === 'REVIEW' ? '服务感受' : '问题说明' }}</text>
      <textarea v-model="content" maxlength="500" :placeholder="mode === 'REVIEW' ? '请写下本次服务的真实感受' : '请说明发生了什么以及希望如何处理'" />
      <view class="attachment"><button type="button" :disabled="submitting" @click="chooseAttachment">{{ selectedFile ? '重新选择附件' : '添加附件（可选）' }}</button><text v-if="selectedFile">{{ selectedFile.name }}</text></view>
      <button class="primary" type="button" :disabled="submitting || uploading" @click="submit">{{ submitting || uploading ? '正在提交' : mode === 'REVIEW' ? '提交评价' : '提交投诉' }}</button>
    </template>
  </view>
</template>

<style scoped>
.feedback-panel{display:grid;gap:20rpx;padding:24rpx;background:#fff;border:1rpx solid #dce8e5;border-radius:8rpx;color:#17312e}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:18rpx}.panel-heading view{display:grid;gap:6rpx}.panel-heading text{font-size:31rpx;font-weight:700}.panel-heading small{color:#6d807c;font-size:22rpx}.panel-heading button,.attachment button{min-height:72rpx;margin:0;padding:0 22rpx;border:1rpx solid #bad2cd;border-radius:6rpx;background:#fff;color:#126f66}.notice,.empty{padding:18rpx;border-radius:6rpx}.success{background:#e5f6f1;color:#0d6d62}.error{background:#fff0ef;color:#ad3b31}.empty{background:#f2f6f5;color:#687b77}.order-list{display:grid;gap:12rpx}.order-list button{display:grid;gap:8rpx;min-height:100rpx;margin:0;padding:18rpx;border:1rpx solid #d6e4e1;border-radius:6rpx;background:#fff;text-align:left;color:#203d38}.order-list button.selected{border-color:#2a9f92;background:#eaf7f4}.order-list strong{font-size:27rpx}.order-list text{color:#6d807c;font-size:23rpx}.mode-tabs,.rating,.tags{display:flex;flex-wrap:wrap;gap:10rpx}.mode-tabs button,.rating button,.tags button{min-height:72rpx;margin:0;padding:0 20rpx;border:1rpx solid #c9d9d6;border-radius:6rpx;background:#fff;color:#526a66}.mode-tabs button.active,.rating button.active,.tags button.active{border-color:#24988b;background:#e4f5f1;color:#0b7568;font-weight:700}.form-section{display:grid;gap:12rpx}.label{display:block;color:#334f4a;font-size:25rpx;font-weight:700}.picker-value,textarea{box-sizing:border-box;width:100%;padding:18rpx;border:1rpx solid #ceddda;border-radius:6rpx;background:#fbfdfc;font-size:26rpx}.picker-value{min-height:78rpx}textarea{min-height:160rpx}.attachment{display:flex;align-items:center;gap:14rpx;min-width:0}.attachment text{min-width:0;color:#637873;font-size:23rpx;overflow-wrap:anywhere}.primary{display:flex;align-items:center;justify-content:center;min-height:88rpx;margin:0;border:0;border-radius:6rpx;background:#0f766e;color:#fff;font-size:28rpx;font-weight:700}.primary:disabled{background:#a8c8c2;color:#f4f8f7}
</style>
