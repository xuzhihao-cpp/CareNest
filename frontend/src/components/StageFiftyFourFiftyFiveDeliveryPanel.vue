<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getDemoDataStatus, getServiceHealth, resetDemoData } from '@/api/stageFortyNineToFiftyFive';
import type { DemoDataStatus, ServiceHealthStatus } from '@/types/stageFortyNineToFiftyFive';

const demo = ref<DemoDataStatus | null>(null);
const health = ref<ServiceHealthStatus | null>(null);
const loading = ref(false);
const resetting = ref(false);
const error = ref('');
const notice = ref('');
const confirmReset = ref(false);
const overallReady = computed(() => Boolean(demo.value?.ready && health.value?.ready));

function formatTime(value: string) {
  if (!value) return '尚未执行';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 19);
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date);
}

async function load() {
  loading.value = true; error.value = ''; notice.value = '';
  const [demoResponse, healthResponse] = await Promise.all([getDemoDataStatus(), getServiceHealth()]);
  loading.value = false;
  if (demoResponse.code === 0) demo.value = demoResponse.data;
  else { demo.value = null; error.value = demoResponse.message || '演示环境状态暂时无法读取。'; }
  if (healthResponse.code === 0) health.value = healthResponse.data;
  else { health.value = null; error.value = error.value || healthResponse.message || '平台运行状态暂时无法读取。'; }
}

async function executeReset() {
  if (!confirmReset.value || resetting.value) return;
  resetting.value = true; error.value = ''; notice.value = '';
  const response = await resetDemoData();
  if (response.code !== 0) { resetting.value = false; error.value = response.message || '演示数据暂时无法重置。'; return; }
  confirmReset.value = false;
  notice.value = '演示数据已恢复为标准场景，正在重新检查交付状态。';
  await load();
  resetting.value = false;
}

onMounted(load);
</script>

<template>
  <view class="delivery-panel">
    <view class="panel-head"><view><text class="eyebrow">交付保障</text><text class="title">演示与运行检查</text><text class="subtitle">确认标准演示场景与平台核心服务均可使用。</text></view><button type="button" class="secondary" @click="load">重新检查</button></view>
    <view v-if="loading" class="state">正在检查平台交付状态...</view><view v-if="error" class="state error">{{ error }}</view><view v-if="notice" class="state success">{{ notice }}</view>
    <view class="readiness" :class="{ ready: overallReady }"><view><text>综合状态</text><strong>{{ overallReady ? '已准备就绪' : '需要检查' }}</strong></view><text>{{ overallReady ? '演示环境和核心服务均可正常使用。' : '请根据下方项目完成检查后再进行演示。' }}</text></view>
    <view class="status-grid">
      <section><view class="section-head"><h3>标准演示场景</h3><text :class="demo?.ready ? 'good' : 'warn'">{{ demo?.ready ? '已就绪' : '未就绪' }}</text></view><dl><div><dt>可用演示账号</dt><dd>{{ demo?.accounts.length ?? 0 }} 个</dd></div><div><dt>标准业务场景</dt><dd>{{ demo?.scenarioCount ?? 0 }} 组</dd></div><div><dt>最近恢复时间</dt><dd>{{ formatTime(demo?.lastResetAt || '') }}</dd></div></dl></section>
      <section><view class="section-head"><h3>平台运行状态</h3><text :class="health?.ready ? 'good' : 'warn'">{{ health?.ready ? '运行正常' : '服务异常' }}</text></view><dl><div><dt>应用服务</dt><dd>{{ health?.status === 'UP' ? '正常' : '异常' }}</dd></div><div><dt>数据服务</dt><dd>{{ health?.databaseConnected ? '连接正常' : '连接异常' }}</dd></div><div><dt>检查时间</dt><dd>{{ formatTime(health?.serverTime || '') }}</dd></div></dl></section>
    </view>
    <section class="reset-area"><view><h3>恢复标准演示基线</h3><p>该操作会重新载入标准账号与业务场景，不删除其他业务记录。</p></view><label class="confirm-row"><checkbox :checked="confirmReset" @click="confirmReset = !confirmReset" /><text>我已了解该操作会恢复标准演示基线</text></label><button type="button" class="danger" :disabled="!confirmReset || resetting" @click="executeReset">{{ resetting ? '正在恢复...' : '恢复标准演示基线' }}</button></section>
  </view>
</template>

<style scoped>
.delivery-panel{padding:24px;border:1px solid #dce7e4;background:#fff;color:#17312e}.panel-head,.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.eyebrow,.title,.subtitle{display:block}.eyebrow{color:#147d72;font-size:12px;font-weight:700}.title{margin-top:6px;font-size:26px;font-weight:700}.subtitle{margin-top:7px;color:#6b7d78;font-size:14px}.secondary,.danger{display:inline-flex;align-items:center;justify-content:center;min-height:42px;margin:0;padding:0 16px;border-radius:5px;font-size:14px}.secondary{border:1px solid #bed0cb;background:#fff;color:#24665e}.readiness{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-top:22px;padding:20px;border-left:5px solid #d39a36;background:#fff8e8}.readiness.ready{border-color:#248f83;background:#edf8f5}.readiness text,.readiness strong{display:block}.readiness>view>text{color:#687b76;font-size:13px}.readiness strong{margin-top:5px;font-size:23px}.readiness>text{color:#586c66;font-size:14px}.status-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-top:18px}.status-grid section,.reset-area{padding:20px;border:1px solid #dce7e4}.section-head h3,.reset-area h3{margin:0;font-size:17px}.section-head>text{padding:5px 9px;border-radius:4px;font-size:12px}.good{background:#dff4ed;color:#08776c}.warn{background:#fff0d7;color:#986414}dl{margin:15px 0 0}dl div{display:flex;justify-content:space-between;gap:16px;padding:10px 0;border-bottom:1px solid #e8efec}dt{color:#667974}dd{margin:0;color:#263f39;font-weight:700}.reset-area{display:grid;grid-template-columns:minmax(260px,1fr) auto auto;align-items:center;gap:24px;margin-top:18px}.reset-area p{margin:8px 0 0;color:#6a7c77;line-height:1.5}.confirm-row{display:flex;align-items:center;gap:8px;color:#596e68;font-size:13px}.danger{border:1px solid #c44f44;background:#c44f44;color:#fff}.danger:disabled{border-color:#cbd7d3;background:#e9efed;color:#8a9995}.state{margin-top:18px;padding:14px 16px;background:#f3f8f6;color:#637670}.state.error{border-left:4px solid #d66b60;background:#fff2f0;color:#a43d34}.state.success{border-left:4px solid #238f83;background:#edf8f5;color:#14766c}@media(max-width:900px){.status-grid{grid-template-columns:1fr}.reset-area{grid-template-columns:1fr}.readiness{align-items:flex-start;flex-direction:column}}
</style>
