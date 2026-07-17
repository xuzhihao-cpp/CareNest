<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { getBasicStatistics, getQualityStatistics } from '@/api/stageFortyNineToFiftyFive';
import type { BasicStatistics, QualityStatistics, TrendPoint } from '@/types/stageFortyNineToFiftyFive';

const props = defineProps<{ canViewBasic: boolean; canViewQuality: boolean }>();
const active = ref<'basic' | 'quality'>(props.canViewBasic ? 'basic' : 'quality');
const basic = ref<BasicStatistics | null>(null);
const quality = ref<QualityStatistics | null>(null);
const loading = ref(false);
const error = ref('');
let controller: AbortController | null = null;
let sequence = 0;

function dateValue(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
const today = new Date(); const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
const dateFrom = ref(dateValue(monthStart)); const dateTo = ref(dateValue(today));
const cardLabels: Record<string, string> = {
  orderCount: '服务订单', totalOrders: '服务订单', completedOrders: '已完成订单',
  activeElders: '服务长辈', activeNurses: '在岗护理', reminderCount: '提醒任务',
  followUpCount: '随访记录', complaintCount: '服务工单'
};
const scoreLabels: Record<string, string> = { EXCELLENT: '优秀', GOOD: '良好', NORMAL: '一般', POOR: '待提升', '90-100': '90 至 100 分', '80-89': '80 至 89 分', '60-79': '60 至 79 分', '0-59': '60 分以下' };
const basicCards = computed(() => basic.value ? Object.entries(basic.value.cards).map(([key, value]) => ({ label: cardLabels[key] || '运营指标', value })) : []);

function percent(value: number) { return `${Math.round(value <= 1 ? value * 100 : value)}%`; }
function score(value: number) { return Number(value).toFixed(1).replace(/\.0$/, ''); }
function maxTrend(records: TrendPoint[]) { return Math.max(1, ...records.map((item) => Math.abs(item.value))); }
function barWidth(value: number, records: TrendPoint[]) { return `${Math.max(4, Math.round(Math.abs(value) / maxTrend(records) * 100))}%`; }
function chartY(value: number, records: TrendPoint[]) { return 92 - Math.round(Math.max(0, value) / maxTrend(records) * 76); }
function chartX(index: number, records: TrendPoint[]) { return records.length < 2 ? 50 : 8 + index / (records.length - 1) * 84; }
function chartPoints(records: TrendPoint[]) { return records.map((item, index) => `${chartX(index, records)},${chartY(item.value, records)}`).join(' '); }
function shortDate(value: string) { return value.length >= 10 ? value.slice(5, 10) : value; }

async function load() {
  if (!dateFrom.value || !dateTo.value || dateTo.value < dateFrom.value) { error.value = '结束日期不能早于开始日期。'; return; }
  controller?.abort(); controller = new AbortController(); const current = ++sequence;
  loading.value = true; error.value = '';
  if (active.value === 'basic') {
    const response = await getBasicStatistics(dateFrom.value, dateTo.value, controller.signal);
    if (current !== sequence) return; loading.value = false;
    if (response.code === 499) return;
    if (response.code === 0) basic.value = response.data; else error.value = response.message || '基础运营统计暂时无法读取。';
  } else {
    const response = await getQualityStatistics(dateFrom.value, dateTo.value, controller.signal);
    if (current !== sequence) return; loading.value = false;
    if (response.code === 499) return;
    if (response.code === 0) quality.value = response.data; else error.value = response.message || '服务质量统计暂时无法读取。';
  }
}

function switchView(value: 'basic' | 'quality') { active.value = value; void load(); }
onMounted(load); onBeforeUnmount(() => { sequence += 1; controller?.abort(); });
</script>

<template>
  <view class="dashboard-panel">
    <view class="panel-head"><view><text class="eyebrow">运营分析</text><text class="title">数据看板</text><text class="subtitle">按时间范围查看服务规模、完成情况与质量趋势。</text></view><button type="button" class="secondary" @click="load">刷新</button></view>
    <view class="toolbar"><view class="segments"><button v-if="canViewBasic" type="button" :class="{ active: active === 'basic' }" @click="switchView('basic')">基础运营</button><button v-if="canViewQuality" type="button" :class="{ active: active === 'quality' }" @click="switchView('quality')">服务质量</button></view><view class="date-range"><picker mode="date" :value="dateFrom" @change="dateFrom = $event.detail.value"><view>{{ dateFrom }}</view></picker><text>至</text><picker mode="date" :value="dateTo" @change="dateTo = $event.detail.value"><view>{{ dateTo }}</view></picker><button type="button" @click="load">查询</button></view></view>
    <view v-if="loading" class="state">正在汇总真实运营数据...</view><view v-else-if="error" class="state error">{{ error }}</view>
    <template v-else-if="active === 'basic' && basic">
      <view class="metric-grid"><view v-for="card in basicCards" :key="card.label" class="metric-card"><text>{{ card.label }}</text><strong>{{ card.value }}</strong></view><view class="metric-card"><text>服务完成率</text><strong>{{ percent(basic.serviceCompletionRate) }}</strong></view><view class="metric-card"><text>提醒完成率</text><strong>{{ percent(basic.reminderDoneRate) }}</strong></view><view class="metric-card"><text>平均满意度</text><strong>{{ score(basic.satisfactionAvg) }}</strong></view></view>
      <section class="trend"><h3>订单趋势</h3><view v-if="basic.orderTrend.length === 1" class="single-trend"><strong>{{ basic.orderTrend[0].value }} 单</strong><text>{{ basic.orderTrend[0].date }}</text><small>当前仅有一个统计日，累计更多日期后将展示订单趋势折线。</small></view><view v-else-if="basic.orderTrend.length > 1" class="line-chart"><svg viewBox="0 0 100 108" preserveAspectRatio="none" role="img" aria-label="订单数量趋势折线图"><line v-for="grid in [16, 40, 64, 88]" :key="grid" x1="8" x2="92" :y1="grid" :y2="grid" class="chart-grid"/><polyline :points="chartPoints(basic.orderTrend)" class="chart-line"/><circle v-for="(point, index) in basic.orderTrend" :key="point.date" :cx="chartX(index, basic.orderTrend)" :cy="chartY(point.value, basic.orderTrend)" r="2.3" class="chart-dot"><title>{{ point.date }}：{{ point.value }} 单</title></circle></svg><view class="chart-labels" :style="{ '--point-count': basic.orderTrend.length }"><view v-for="point in basic.orderTrend" :key="point.date" class="chart-label"><strong>{{ point.value }} 单</strong><text>{{ shortDate(point.date) }}</text></view></view></view><view v-else class="state">当前日期范围暂无订单趋势。</view></section>
    </template>
    <template v-else-if="active === 'quality' && quality">
      <view class="metric-grid"><view class="metric-card"><text>档案完整率</text><strong>{{ percent(quality.archiveCompleteRate) }}</strong></view><view class="metric-card"><text>指标达标率</text><strong>{{ percent(quality.metricPassRate) }}</strong></view><view class="metric-card"><text>异常审核通过率</text><strong>{{ percent(quality.exceptionApproveRate) }}</strong></view></view>
      <view class="quality-layout"><section class="distribution"><h3>评分分布</h3><view v-for="(value, key) in quality.scoreDistribution" :key="key" class="distribution-row"><text>{{ scoreLabels[key] || '其他评分' }}</text><strong>{{ value }} 人</strong></view><view v-if="!Object.keys(quality.scoreDistribution).length" class="state">当前日期范围暂无评分记录。</view></section><section class="trend"><h3>质量趋势</h3><view v-for="point in quality.qualityTrend" :key="point.date" class="trend-row"><text>{{ point.date }}</text><view><i :style="{ width: barWidth(point.value, quality.qualityTrend) }" /></view><strong>{{ score(point.value) }}</strong></view><view v-if="!quality.qualityTrend.length" class="state">当前日期范围暂无质量趋势。</view></section></view>
    </template>
  </view>
</template>

<style scoped>
.dashboard-panel{padding:24px;border:1px solid #dce7e4;background:#fff;color:#17312e}.panel-head,.toolbar{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.eyebrow,.title,.subtitle{display:block}.eyebrow{color:#147d72;font-size:12px;font-weight:700}.title{margin-top:6px;font-size:26px;font-weight:700}.subtitle{margin-top:7px;color:#6c7e79;font-size:14px}.toolbar{align-items:center;margin-top:24px;padding:14px;background:#f4f8f6}.segments,.date-range{display:flex;align-items:center;gap:8px}.segments button,.date-range button,.secondary{display:inline-flex;align-items:center;justify-content:center;min-height:40px;margin:0;padding:0 14px;border:1px solid #bfd1cc;border-radius:5px;background:#fff;color:#365f57;font-size:13px}.segments button.active{border-color:#228f84;background:#e6f5f1;color:#08766b;font-weight:700}.date-range picker view{min-width:112px;padding:11px 13px;border:1px solid #ccdcd7;border-radius:5px;background:#fff;font-size:13px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-top:20px}.metric-card{display:grid;gap:10px;padding:19px;border-left:4px solid #2b988d;background:#f3f8f6}.metric-card text{color:#60736e;font-size:13px}.metric-card strong{font-size:27px}.trend,.distribution{margin-top:20px;padding:20px;border:1px solid #dce7e4}.trend h3,.distribution h3{margin:0 0 16px;font-size:17px}.single-trend{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:6px 18px;min-height:104px;padding:20px 24px;border-left:4px solid #168d82;background:#f3f8f6}.single-trend strong{font-size:32px;line-height:1;color:#173f38}.single-trend text{color:#58736d;font-size:14px}.single-trend small{grid-column:1/-1;color:#758782;font-size:13px;line-height:1.6}.line-chart{padding:12px 4px 0}.line-chart svg{display:block;width:100%;height:220px;overflow:visible}.chart-grid{stroke:#e1ece8;stroke-width:.6}.chart-line{fill:none;stroke:#168d82;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}.chart-dot{fill:#fff;stroke:#168d82;stroke-width:1.25}.chart-labels{display:grid;grid-template-columns:repeat(var(--point-count),minmax(0,1fr));gap:4px;margin-top:4px}.chart-label{display:grid;gap:3px;min-width:0;text-align:center;color:#637670;font-size:12px}.chart-label strong{color:#21483f;font-size:13px}.trend-row{display:grid;grid-template-columns:100px minmax(80px,1fr) 60px;align-items:center;gap:12px;margin-top:11px;color:#5e716c;font-size:13px}.trend-row>view{height:10px;background:#e7efec}.trend-row i{display:block;height:100%;background:#2b998e}.trend-row strong{text-align:right;color:#21483f}.quality-layout{display:grid;grid-template-columns:minmax(250px,.7fr) minmax(420px,1.3fr);gap:16px}.distribution-row{display:flex;justify-content:space-between;padding:11px 0;border-bottom:1px solid #e8efec;color:#576c66}.state{margin-top:20px;padding:18px;background:#f4f8f6;color:#6b7d78;text-align:center}.state.error{border-left:4px solid #d66b60;background:#fff2f0;color:#a43d34}@media(max-width:900px){.toolbar{align-items:stretch;flex-direction:column}.date-range{flex-wrap:wrap}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.quality-layout{grid-template-columns:1fr}}@media(max-width:560px){.metric-grid{grid-template-columns:1fr}.single-trend{grid-template-columns:1fr;gap:8px;padding:18px}.line-chart svg{height:170px}.chart-labels{overflow-x:auto;grid-auto-columns:76px;grid-auto-flow:column;grid-template-columns:none}.trend-row{grid-template-columns:78px minmax(60px,1fr) 48px}}
</style>
