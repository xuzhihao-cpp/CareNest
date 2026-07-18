<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getAdminNurseScore,
  handleComplaint,
  listComplaints,
  listNurseAppeals,
  recalculateNurseScore,
  reviewNurseAppeal
} from '@/api/stageFortyFourToFortyEight';
import type { AdminNurseScore, NurseAppeal, ReviewComplaintResult } from '@/types/stageFortyFourToFortyEight';

type Tab = 'COMPLAINT' | 'APPEAL';
const tab = ref<Tab>('COMPLAINT');
const complaints = ref<ReviewComplaintResult[]>([]);
const appeals = ref<NurseAppeal[]>([]);
const selectedComplaint = ref<ReviewComplaintResult | null>(null);
const selectedAppeal = ref<NurseAppeal | null>(null);
const score = ref<AdminNurseScore | null>(null);
const comment = ref('');
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const complaintFieldError = ref('');
const adminAppealFieldError = ref('');
const notice = ref('');

const pendingComplaints = computed(() => complaints.value.filter((item) =>
  item.status === 'PENDING' || item.status === 'PROCESSING'
));
const pendingAppeals = computed(() => appeals.value.filter((item) => item.status === 'PENDING'));
const complaintLabels: Record<string, string> = {
  PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', REJECTED: '不成立'
};
const appealLabels: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '未通过' };

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待同步';
}

async function load() {
  loading.value = true;
  error.value = '';
  const [complaintResponse, appealResponse] = await Promise.all([listComplaints(), listNurseAppeals()]);
  loading.value = false;
  if (complaintResponse.code === 0) complaints.value = complaintResponse.data;
  else {
    complaints.value = [];
    error.value = complaintResponse.code === 403 ? '当前账号没有处理投诉的权限。' : '投诉列表暂时无法读取。';
  }
  if (appealResponse.code === 0) appeals.value = appealResponse.data;
  else if (!error.value) error.value = appealResponse.code === 403 ? '当前账号没有审核护理申诉的权限。' : '护理申诉暂时无法读取。';
  if (selectedComplaint.value) selectedComplaint.value = complaints.value.find((item) => item.complaintId === selectedComplaint.value?.complaintId) || null;
  if (selectedAppeal.value) selectedAppeal.value = appeals.value.find((item) => item.appealId === selectedAppeal.value?.appealId) || null;
}

function selectComplaint(item: ReviewComplaintResult) {
  selectedComplaint.value = item;
  comment.value = item.handleResult || '';
  notice.value = '';
  error.value = '';
  complaintFieldError.value = '';
}

async function selectAppeal(item: NurseAppeal) {
  selectedAppeal.value = item;
  score.value = null;
  comment.value = item.reviewComment || '';
  notice.value = '';
  error.value = '';
  adminAppealFieldError.value = '';
  const response = await getAdminNurseScore(item.nurseId);
  if (selectedAppeal.value?.appealId !== item.appealId) return;
  if (response.code === 0) score.value = response.data;
  else error.value = '护理评分暂时无法读取，审核入口已保留但请先刷新确认。';
}

async function decideComplaint(status: 'RESOLVED' | 'REJECTED') {
  if (!selectedComplaint.value?.complaintId || submitting.value) return;
  complaintFieldError.value = '';
  if (comment.value.trim().length === 0) {
    complaintFieldError.value = '请先填写处理说明。';
    return;
  }
  const id = selectedComplaint.value.complaintId;
  submitting.value = true;
  const response = await handleComplaint(id, status, comment.value.trim());
  submitting.value = false;
  if (selectedComplaint.value?.complaintId !== id) return;
  if (response.code !== 0) {
    complaintFieldError.value = response.code === 409 ? '投诉状态已被其他人员更新，请刷新。' : '投诉处理未完成，请稍后重试。';
    return;
  }
  notice.value = status === 'RESOLVED' ? '投诉已记录为已解决。' : '投诉已记录为不成立。';
  await load();
}

async function decideAppeal(decision: 'APPROVED' | 'REJECTED') {
  if (!selectedAppeal.value || submitting.value) return;
  adminAppealFieldError.value = '';
  if (comment.value.trim().length === 0) {
    adminAppealFieldError.value = '请先填写审核说明。';
    return;
  }
  const current = selectedAppeal.value;
  submitting.value = true;
  const response = await reviewNurseAppeal(current.appealId, current.targetId, decision, comment.value.trim());
  submitting.value = false;
  if (selectedAppeal.value?.appealId !== current.appealId) return;
  if (response.code !== 0) {
    adminAppealFieldError.value = response.code === 409 ? '该申诉已由其他人员处理，请刷新。' : '申诉审核未完成，请稍后重试。';
    return;
  }
  notice.value = decision === 'APPROVED' ? '申诉已通过，护理评分已按事实重新核算。' : '申诉未通过，原评分保持不变。';
  await load();
  await selectAppeal(appeals.value.find((item) => item.appealId === current.appealId) || response.data);
}

async function recalculate() {
  if (!selectedAppeal.value || submitting.value) return;
  const current = selectedAppeal.value;
  submitting.value = true;
  const response = await recalculateNurseScore(current.nurseId, current.appealId);
  submitting.value = false;
  if (selectedAppeal.value?.appealId !== current.appealId) return;
  if (response.code === 0) {
    score.value = response.data;
    notice.value = '护理评分已按当前服务指标、投诉和申诉事实重新核算。';
  } else error.value = '评分重新核算未完成，请稍后重试。';
}

onMounted(load);
</script>

<template>
  <view class="support-admin">
    <view class="heading"><view><text>服务监督</text><small>处理家属投诉、护理申诉与评分复核</small></view><button type="button" :disabled="loading" @click="load">刷新</button></view>
    <view class="tabs"><button type="button" :class="{ active: tab === 'COMPLAINT' }" @click="tab='COMPLAINT'">投诉处理 {{ pendingComplaints.length }}</button><button type="button" :class="{ active: tab === 'APPEAL' }" @click="tab='APPEAL'">申诉审核 {{ pendingAppeals.length }}</button></view>
    <view v-if="notice" class="notice success">{{ notice }}</view><view v-if="error" class="notice error">{{ error }}</view>
    <view v-if="tab === 'COMPLAINT'" class="workbench">
      <view class="list"><view class="list-title">投诉记录 <small>{{ complaints.length }} 项</small></view><view v-if="!complaints.length" class="empty">暂无投诉记录。</view><button v-for="item in complaints" :key="item.complaintId || item.orderId" type="button" :class="{ selected: selectedComplaint?.complaintId === item.complaintId }" @click="selectComplaint(item)"><strong>{{ item.serviceName || '上门护理服务' }}</strong><text>{{ item.complainantName || '家属用户' }} · {{ complaintLabels[item.status] || '处理中' }}</text><small>{{ item.content || '未填写问题说明' }}</small></button></view>
      <view class="detail"><view v-if="!selectedComplaint" class="empty">从左侧选择一项投诉查看和处理。</view><template v-else><view class="detail-title"><strong>{{ selectedComplaint.serviceName || '上门护理服务' }}</strong><span>{{ complaintLabels[selectedComplaint.status] || selectedComplaint.status }}</span></view><dl><dt>提交人</dt><dd>{{ selectedComplaint.complainantName || '家属用户' }}</dd><dt>问题类型</dt><dd>{{ selectedComplaint.reasonType || '服务问题' }}</dd><dt>问题说明</dt><dd>{{ selectedComplaint.content }}</dd><dt>提交时间</dt><dd>{{ formatTime(selectedComplaint.createdAt) }}</dd></dl><template v-if="selectedComplaint.status === 'PENDING' || selectedComplaint.status === 'PROCESSING'"><textarea v-model="comment" maxlength="1000" placeholder="填写调查情况和处理说明"/><view v-if="complaintFieldError" class="complaint-field-error">{{ complaintFieldError }}</view><view class="actions"><button type="button" :disabled="submitting" @click="decideComplaint('REJECTED')">记录为不成立</button><button class="primary" type="button" :disabled="submitting" @click="decideComplaint('RESOLVED')">确认已解决</button></view></template><view v-else class="result">处理结果：{{ selectedComplaint.handleResult || '已完成处理' }}</view></template></view>
    </view>
    <view v-else class="workbench">
      <view class="list"><view class="list-title">护理申诉 <small>{{ appeals.length }} 项</small></view><view v-if="!appeals.length" class="empty">暂无护理申诉。</view><button v-for="item in appeals" :key="item.appealId" type="button" :class="{ selected: selectedAppeal?.appealId === item.appealId }" @click="selectAppeal(item)"><strong>{{ item.nurseName || '护理人员' }}</strong><text>{{ item.targetLabel }} · {{ appealLabels[item.status] }}</text><small>{{ item.reason || '未填写申诉说明' }}</small></button></view>
      <view class="detail"><view v-if="!selectedAppeal" class="empty">从左侧选择一项申诉查看评分和审核。</view><template v-else><view class="detail-title"><strong>{{ selectedAppeal.nurseName || '护理人员' }}</strong><span>{{ appealLabels[selectedAppeal.status] }}</span></view><dl><dt>复核事项</dt><dd>{{ selectedAppeal.targetLabel }}</dd><dt>申诉说明</dt><dd>{{ selectedAppeal.reason }}</dd><dt>提交时间</dt><dd>{{ formatTime(selectedAppeal.createdAt) }}</dd></dl><view v-if="score" class="score-card"><view><text>当前评分</text><strong>{{ Number(score.totalScore).toFixed(1) }}</strong></view><button type="button" :disabled="submitting" @click="recalculate">重新核算</button><small v-if="score.changeLogs[0]">最近变化：{{ score.changeLogs[0].reason }}</small></view><template v-if="selectedAppeal.status === 'PENDING'"><textarea v-model="comment" maxlength="500" placeholder="填写审核依据和结论"/><view v-if="adminAppealFieldError" class="admin-appeal-field-error">{{ adminAppealFieldError }}</view><view class="actions"><button type="button" :disabled="submitting" @click="decideAppeal('REJECTED')">申诉不通过</button><button class="primary" type="button" :disabled="submitting || !score" @click="decideAppeal('APPROVED')">通过并重算评分</button></view></template><view v-else class="result">审核说明：{{ selectedAppeal.reviewComment || '审核已完成' }}</view></template></view>
    </view>
  </view>
</template>

<style scoped>
.support-admin{display:grid;gap:18px}.heading,.detail-title,.actions,.score-card>view{display:flex;align-items:center;justify-content:space-between;gap:14px}.heading>view{display:grid;gap:4px}.heading text{font-size:24px;font-weight:700}.heading small{color:#6f817e}.heading button,.actions button,.score-card button{min-height:42px;margin:0;padding:0 18px;border:1px solid #bdd1cd;border-radius:6px;background:#fff;color:#146d63}.tabs{display:flex;gap:8px}.tabs button{min-height:44px;margin:0;padding:0 20px;border:1px solid #c8d8d5;border-radius:6px;background:#fff;color:#516965}.tabs button.active{border-color:#23998c;background:#e7f6f2;color:#0c7367;font-weight:700}.notice,.empty,.result{padding:16px;border-radius:6px}.success{background:#e5f6f1;color:#0d6e62}.error{background:#fff0ef;color:#ac3c32}.workbench{display:grid;grid-template-columns:minmax(280px,.85fr) minmax(380px,1.4fr);height:min(690px,calc(100vh - 230px));min-height:560px;overflow:hidden;border:1px solid #dbe7e4;background:#fff}.list,.detail{min-width:0;padding:20px;box-sizing:border-box}.list{max-height:100%;overflow-y:auto;border-right:1px solid #dbe7e4}.list-title{position:sticky;top:-20px;z-index:2;display:flex;justify-content:space-between;margin:-20px -20px 12px;padding:20px 20px 12px;border-bottom:1px solid #eef4f2;background:#fff;font-size:18px;font-weight:700}.list-title small{color:#71827f;font-weight:400}.list>button{display:grid;gap:7px;width:100%;margin:0 0 10px;padding:14px;border:1px solid #dce7e5;border-radius:6px;background:#fff;text-align:left;color:#1b3732}.list>button.selected{border-color:#289b8e;background:#eaf7f4}.list>button text,.list>button small{color:#667b76;overflow-wrap:anywhere}.detail{display:grid;align-content:start;gap:16px;min-height:0;overflow-y:auto}.detail-title strong{font-size:21px}.detail-title span{padding:6px 12px;border-radius:999px;background:#edf4f2;color:#3f625c}dl{display:grid;grid-template-columns:88px minmax(0,1fr);gap:12px;margin:0;padding:16px;background:#f6f9f8}dt{color:#70817e}dd{margin:0;overflow-wrap:anywhere}textarea{box-sizing:border-box;width:100%;min-height:120px;padding:14px;border:1px solid #ceddda;border-radius:6px;background:#fbfdfc}.complaint-field-error,.admin-appeal-field-error{margin-top:-8px;color:#ac3c32;font-size:14px}.actions{justify-content:flex-end}.actions .primary{border-color:#0f766e;background:#0f766e;color:#fff}.result{background:#eef6f4;color:#315d55}.score-card{display:grid;grid-template-columns:1fr auto;gap:10px;padding:16px;border:1px solid #bcd9d4;background:#f0faf7}.score-card>view{justify-content:flex-start}.score-card strong{font-size:28px;color:#0f766e}.score-card small{grid-column:1/-1;color:#667b76}.empty{background:#f3f7f6;color:#6c7d7a}@media(max-width:900px){.workbench{grid-template-columns:1fr;height:auto;min-height:0;overflow:visible}.list{max-height:420px;border-right:0;border-bottom:1px solid #dbe7e4}.detail{max-height:none;overflow:visible}}
</style>
