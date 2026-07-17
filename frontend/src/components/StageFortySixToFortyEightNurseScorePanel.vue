<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { uploadMedicalFileAsset } from '@/api/stageTwenty';
import { getMyScore, listNurseAppeals, submitNurseAppeal } from '@/api/stageFortyFourToFortyEight';
import type { NurseAppeal, NurseScore, ScoreChangeItem } from '@/types/stageFortyFourToFortyEight';

const score = ref<NurseScore | null>(null);
const appeals = ref<NurseAppeal[]>([]);
const selectedChange = ref<ScoreChangeItem | null>(null);
const reason = ref('');
const selectedFile = ref<{ path: string; name: string } | null>(null);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const appealFieldError = ref('');
const notice = ref('');

const appealableItems = computed(() => (score.value?.items || []).filter((item) =>
  item.scoreDelta < 0 && item.targetType && item.targetId
));

const levelLabel = computed(() => ({
  EXCELLENT: '表现优秀', GOOD: '表现良好', NEEDS_IMPROVEMENT: '需要改进'
}[score.value?.level || 'GOOD']));

const statusLabels: Record<NurseAppeal['status'], string> = {
  PENDING: '待审核', APPROVED: '申诉通过', REJECTED: '申诉未通过'
};

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待同步';
}

async function load() {
  loading.value = true;
  error.value = '';
  const [scoreResponse, appealResponse] = await Promise.all([getMyScore(), listNurseAppeals()]);
  loading.value = false;
  if (scoreResponse.code !== 0) {
    score.value = null;
    error.value = scoreResponse.code === 403 ? '当前账号没有查看护理评分的权限。' : '护理评分暂时无法读取。';
  } else score.value = scoreResponse.data;
  appeals.value = appealResponse.code === 0 ? appealResponse.data : [];
}

function chooseAttachment() {
  uni.chooseFile({
    count: 1, type: 'all', extension: ['pdf', 'jpg', 'jpeg', 'png'],
    success(result) {
      const files = (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) as Array<File | { path: string; name?: string; size: number }>;
      const file = files[0] as { path?: string; name?: string; size: number } | undefined;
      if (!file) return;
      const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths];
      const path = paths[0] || file.path || '';
      const name = file.name || path.split('/').pop() || '申诉附件';
      if (file.size > 10 * 1024 * 1024 || !/\.(pdf|jpe?g|png)$/i.test(name)) {
        error.value = '附件仅支持 PDF、JPG、PNG，且不能超过 10 MB。';
        return;
      }
      selectedFile.value = { path, name };
      error.value = '';
    }
  });
}

async function submitAppeal() {
  if (!selectedChange.value?.targetType || !selectedChange.value.targetId || submitting.value) return;
  appealFieldError.value = '';
  if (reason.value.trim().length === 0) {
    appealFieldError.value = '请先填写申诉理由。';
    return;
  }
  submitting.value = true;
  error.value = '';
  notice.value = '';
  let fileIds: string[] = [];
  if (selectedFile.value) {
    const upload = await uploadMedicalFileAsset(selectedFile.value.path, () => undefined);
    if (upload.code !== 0 || !upload.data.fileId?.trim()) {
      appealFieldError.value = '申诉附件上传失败，请稍后重试。';
      submitting.value = false;
      return;
    }
    fileIds = [upload.data.fileId.trim()];
  }
  const response = await submitNurseAppeal({
    targetType: selectedChange.value.targetType,
    targetId: selectedChange.value.targetId,
    reason: reason.value.trim(),
    fileIds
  });
  submitting.value = false;
  if (response.code !== 0) {
    appealFieldError.value = response.code === 409 ? '该评分事项已有待审核申诉，请勿重复提交。'
      : response.code === 403 ? '当前账号没有提交护理申诉的权限。'
      : '申诉提交未完成，请检查内容后重试。';
    return;
  }
  notice.value = '申诉已提交，审核期间原评分保持不变。';
  selectedChange.value = null;
  reason.value = '';
  selectedFile.value = null;
  appealFieldError.value = '';
  await load();
}

onMounted(load);
</script>

<template>
  <view class="score-panel">
    <view class="heading"><view><text>我的护理评分</text><small>查看评分变化、扣分来源和申诉进度</small></view><button type="button" :disabled="loading" @click="load">刷新</button></view>
    <view v-if="notice" class="notice success">{{ notice }}</view>
    <view v-if="error" class="notice error">{{ error }}</view>
    <template v-if="score">
      <view class="score-summary"><strong>{{ Number(score.totalScore).toFixed(1) }}</strong><view><text>{{ levelLabel }}</text><small>本月变化 {{ score.monthDelta > 0 ? '+' : '' }}{{ score.monthDelta }}</small></view></view>
      <view class="section"><view class="section-title"><text>评分变化</text><small>{{ score.items.length }} 条</small></view><view v-if="!score.items.length" class="empty">暂无评分变化记录。</view><button v-for="item in score.items" :key="item.changeLogId" type="button" class="change-row" :class="{ selectable: item.scoreDelta < 0 && item.targetType, selected: selectedChange?.changeLogId === item.changeLogId }" :disabled="!(item.scoreDelta < 0 && item.targetType)" @click="selectedChange=item"><view><strong>{{ item.reason }}</strong><text>{{ formatTime(item.createdAt) }}</text></view><b :class="item.scoreDelta >= 0 ? 'positive' : 'negative'">{{ item.scoreDelta > 0 ? '+' : '' }}{{ item.scoreDelta }} 分</b></button></view>
      <view v-if="selectedChange" class="appeal-form"><strong>申请复核此项扣分</strong><text>{{ selectedChange.reason }}</text><textarea v-model="reason" maxlength="1000" placeholder="说明实际情况、异议依据和希望复核的内容"/><view v-if="appealFieldError" class="appeal-field-error">{{ appealFieldError }}</view><view class="attachment"><button type="button" @click="chooseAttachment">{{ selectedFile ? '重新选择附件' : '添加证明（可选）' }}</button><text v-if="selectedFile">{{ selectedFile.name }}</text></view><view class="form-actions"><button type="button" @click="selectedChange=null">取消</button><button class="primary" type="button" :disabled="submitting" @click="submitAppeal">{{ submitting ? '正在提交' : '提交申诉' }}</button></view></view>
      <view class="section"><view class="section-title"><text>申诉记录</text><small>{{ appeals.length }} 条</small></view><view v-if="!appeals.length" class="empty">暂无申诉记录。</view><view v-for="appeal in appeals" :key="appeal.appealId" class="appeal-row"><view><strong>{{ appeal.targetLabel }}</strong><text>{{ appeal.reason }}</text><small>{{ formatTime(appeal.createdAt) }}</small></view><span :class="`status ${appeal.status.toLowerCase()}`">{{ statusLabels[appeal.status] }}</span><text v-if="appeal.reviewComment">审核说明：{{ appeal.reviewComment }}</text></view></view>
    </template>
  </view>
</template>

<style scoped>
.score-panel{display:grid;gap:18rpx}.heading,.score-summary,.section-title,.form-actions,.attachment{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.heading view{display:grid;gap:6rpx}.heading text{font-size:31rpx;font-weight:700}.heading small,.section-title small{color:#70817e;font-size:22rpx}.heading button,.attachment button,.form-actions button{min-height:76rpx;margin:0;padding:0 22rpx;border:1rpx solid #bfd2ce;border-radius:6rpx;background:#fff;color:#176d63}.notice,.empty{padding:18rpx;border-radius:6rpx}.success{background:#e5f6f1;color:#0e6f63}.error{background:#fff0ef;color:#b13e34}.score-summary{padding:24rpx;background:#0f766e;color:#fff;border-radius:8rpx}.score-summary strong{font-size:52rpx}.score-summary view{display:grid;gap:6rpx;text-align:right}.score-summary text{font-size:27rpx;font-weight:700}.score-summary small{font-size:22rpx;opacity:.82}.section,.appeal-form{display:grid;gap:12rpx;padding:20rpx;background:#fff;border:1rpx solid #dce7e5;border-radius:8rpx}.section-title text{font-size:28rpx;font-weight:700}.change-row{display:flex;align-items:center;justify-content:space-between;gap:16rpx;width:100%;min-height:106rpx;margin:0;padding:18rpx;border:1rpx solid #e0e9e7;border-radius:6rpx;background:#fff;text-align:left}.change-row view,.appeal-row>view{display:grid;gap:7rpx;min-width:0}.change-row strong,.appeal-row strong{color:#1d3834;font-size:25rpx}.change-row text,.appeal-row text,.appeal-row small{color:#6b7e7a;font-size:22rpx}.change-row.selectable{border-color:#9bc9c1}.change-row.selected{background:#eaf7f4;border-color:#249b8e}.change-row b{flex:none}.positive{color:#087466}.negative{color:#b24a3e}.appeal-form textarea{box-sizing:border-box;width:100%;min-height:150rpx;padding:16rpx;border:1rpx solid #ccdcda;border-radius:6rpx;background:#fbfdfc;font-size:25rpx}.appeal-field-error{color:#b13e34;font-size:22rpx}.attachment{justify-content:flex-start;min-width:0}.attachment text{min-width:0;overflow-wrap:anywhere;color:#667a76;font-size:22rpx}.form-actions{justify-content:flex-end}.form-actions .primary{border-color:#0f766e;background:#0f766e;color:#fff}.appeal-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10rpx;padding:18rpx;border:1rpx solid #e0e9e7;border-radius:6rpx}.appeal-row>text{grid-column:1/-1}.status{align-self:start;padding:6rpx 12rpx;border-radius:999rpx;background:#fff0d9;color:#946100;font-size:21rpx}.status.approved{background:#def4ed;color:#0c7466}.status.rejected{background:#fde9e7;color:#a64238}.empty{background:#f2f6f5;color:#6b7c79;font-size:23rpx}
</style>
