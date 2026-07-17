<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { getQualificationSkillOptions } from '@/api/stageTwentySix';
import { getOrderRecommendations, recommendNurses } from '@/api/stageTwentyNine';
import { createLatestRequestGate } from '@/utils/latestRequestGate';
import type { NurseRecommendationRecord, NurseRecommendationRequest } from '@/types/stageTwentyNine';
import type { QualificationSkillOption } from '@/types/stageTwentySix';
import {
  recommendationConditionKey,
  recommendationConditionsComplete,
  recommendationErrorMessage,
  recommendationScoreText,
  recommendationSkillDictionaryErrorMessage
} from '@/utils/stageTwentyNineRules';

const props = withDefaults(defineProps<{
  mode?: 'conditions' | 'order';
  elderId?: string;
  serviceId?: string;
  addressId?: string;
  scheduledStart?: string;
  orderId?: string;
  selectable?: boolean;
  selectedNurseId?: string;
}>(), {
  mode: 'conditions',
  elderId: '',
  serviceId: '',
  addressId: '',
  scheduledStart: '',
  orderId: '',
  selectable: false,
  selectedNurseId: ''
});

const emit = defineEmits<{
  invalidated: [];
  selected: [record: NurseRecommendationRecord];
  recommendationsLoaded: [records: NurseRecommendationRecord[]];
}>();

const records = ref<NurseRecommendationRecord[]>([]);
const loading = ref(false);
const loaded = ref(false);
const error = ref('');
const skills = ref<QualificationSkillOption[]>([]);
const skillDictionaryError = ref('');
const skillDictionaryLoading = ref(false);
const recommendationRequestGate = createLatestRequestGate<string>();
const skillRequestGate = createLatestRequestGate<string>();

const conditions = computed<NurseRecommendationRequest>(() => ({
  elderId: props.elderId,
  serviceId: props.serviceId,
  addressId: props.addressId,
  scheduledStart: props.scheduledStart
}));
const conditionsReady = computed(() => recommendationConditionsComplete(conditions.value));
const contextKey = computed(() => props.mode === 'order'
  ? `order:${props.orderId.trim()}`
  : `conditions:${recommendationConditionKey(conditions.value)}`);
const skillLabelMap = computed(() => new Map(
  skills.value.map((item) => [item.value, item.label])
));
const hasUnresolvedSkills = computed(() => !skillDictionaryLoading.value
  && !skillDictionaryError.value
  && records.value.some((record) =>
    record.matchedSkills.some((code) => !skillLabelMap.value.has(code))
  ));

function visibleSkillLabels(record: NurseRecommendationRecord) {
  return record.matchedSkills
    .map((code) => skillLabelMap.value.get(code) ?? '')
    .filter(Boolean);
}

async function loadSkillDictionary() {
  const requestTicket = skillRequestGate.begin('nurseServiceSkill');
  skillDictionaryLoading.value = true;
  skillDictionaryError.value = '';
  const response = await getQualificationSkillOptions();
  if (!skillRequestGate.isCurrent(requestTicket, 'nurseServiceSkill')) return;
  skillDictionaryLoading.value = false;
  skills.value = response.code === 0 ? response.data : [];
  skillDictionaryError.value = recommendationSkillDictionaryErrorMessage(response.code);
}

function clearResults(shouldEmit = true) {
  recommendationRequestGate.invalidate();
  loading.value = false;
  loaded.value = false;
  records.value = [];
  error.value = '';
  emit('recommendationsLoaded', []);
  if (shouldEmit && props.mode === 'conditions') emit('invalidated');
}

function selectRecommendation(record: NurseRecommendationRecord) {
  if (!props.selectable || !record.available) return;
  emit('selected', record);
}

async function loadRecommendations() {
  if (props.mode === 'conditions' && !conditionsReady.value) {
    clearResults(false);
    error.value = '请先完整选择服务对象、服务、地址和预约时间。';
    return;
  }
  if (props.mode === 'order' && !props.orderId.trim()) {
    clearResults(false);
    return;
  }
  const requestedContext = contextKey.value;
  const requestTicket = recommendationRequestGate.begin(requestedContext);
  loading.value = true;
  loaded.value = false;
  error.value = '';
  const response = props.mode === 'order'
    ? await getOrderRecommendations(props.orderId)
    : await recommendNurses(conditions.value);
  if (!recommendationRequestGate.isCurrent(requestTicket, contextKey.value)) return;
  loading.value = false;
  loaded.value = true;
  if (response.code !== 0) {
    records.value = [];
    emit('recommendationsLoaded', []);
    error.value = recommendationErrorMessage(response.code, props.mode);
    return;
  }
  records.value = response.data.nurses;
  emit('recommendationsLoaded', records.value);
}

watch(contextKey, () => {
  clearResults();
  if (props.mode === 'order' && props.orderId.trim()) void loadRecommendations();
}, { immediate: true });

onMounted(loadSkillDictionary);

onBeforeUnmount(() => {
  recommendationRequestGate.invalidate();
  skillRequestGate.invalidate();
});
</script>

<template>
  <section class="recommendation-panel" :class="{ compact: mode === 'order' }">
    <header class="recommendation-heading">
      <view>
        <text class="recommendation-title">{{ mode === 'order' ? '本单护理推荐' : '推荐护理' }}</text>
        <text class="recommendation-subtitle">{{ mode === 'order' ? '查看下单时形成的推荐结果和原因' : '根据本次服务条件获取合适的护理人员' }}</text>
      </view>
      <button type="button" :disabled="loading || (mode === 'conditions' && !conditionsReady)" @click="loadRecommendations">
        {{ loading ? '正在获取' : loaded ? '重新获取' : '获取推荐' }}
      </button>
    </header>

    <view v-if="skillDictionaryError" class="recommendation-message warning">
      <text>{{ skillDictionaryError }}</text>
      <button type="button" :disabled="skillDictionaryLoading" @click="loadSkillDictionary">重新读取技能名称</button>
    </view>
    <view v-else-if="hasUnresolvedSkills" class="recommendation-message warning">部分匹配技能尚未配置中文名称，暂不显示这些技能，请联系平台维护人员。</view>
    <view v-if="error" class="recommendation-message error" role="alert">{{ error }}</view>
    <view v-else-if="loading" class="recommendation-message">正在根据服务条件匹配护理人员...</view>
    <view v-else-if="mode === 'conditions' && !conditionsReady" class="recommendation-message">完成服务对象、服务、地址和预约时间选择后，可获取护理推荐。</view>
    <view v-else-if="loaded && !records.length" class="recommendation-message empty">当前条件下暂无合适的护理人员，可以调整预约时间后重试。</view>

    <view v-if="records.length" class="recommendation-list">
      <article
        v-for="item in records"
        :key="item.nurseId"
        class="recommendation-card"
        :class="{ unavailable: !item.available, selected: selectedNurseId === item.nurseId }"
      >
        <view class="recommendation-card-heading">
          <view><text class="nurse-name">{{ item.nurseName }}</text><text class="score">综合评分 {{ recommendationScoreText(item.score) }} 分</text></view>
          <text class="availability" :class="{ unavailable: !item.available }">{{ item.available ? '当前可预约' : '当前不可预约' }}</text>
        </view>
        <view v-if="visibleSkillLabels(item).length" class="skill-row">
          <text v-for="skill in visibleSkillLabels(item)" :key="skill" class="skill-chip">{{ skill }}</text>
        </view>
        <text class="recommendation-reason">{{ item.recommendReason }}</text>
        <button
          v-if="selectable"
          class="preference-button"
          type="button"
          :disabled="!item.available || selectedNurseId === item.nurseId"
          @click="selectRecommendation(item)"
        >
          {{ selectedNurseId === item.nurseId ? '已选为偏好' : item.available ? '设为偏好' : '当前不可选择' }}
        </button>
      </article>
    </view>
    <text v-if="selectable && records.length" class="preference-note">偏好护理用于表达服务意愿，最终安排以平台派单结果为准。</text>
  </section>
</template>

<style scoped>
.recommendation-panel { display:grid; gap:14rpx; margin-top:24rpx; padding:22rpx; border:1rpx solid #d4e4e0; border-radius:9rpx; background:#f8fbfa; }.recommendation-heading,.recommendation-card-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }.recommendation-title,.recommendation-subtitle,.nurse-name,.score,.recommendation-reason,.recommendation-message > text { display:block; }.recommendation-title { color:#173c36; font-size:28rpx; font-weight:800; }.recommendation-subtitle { margin-top:6rpx; color:#70827e; font-size:21rpx; line-height:1.45; }.recommendation-heading button,.recommendation-message button,.preference-button { display:inline-flex; align-items:center; justify-content:center; flex:none; min-width:142rpx; min-height:80rpx; margin:0; padding:0 18rpx; border:1rpx solid #9fc9c1; border-radius:6rpx; background:#fff; color:#126f65; font-size:23rpx; font-weight:700; line-height:1.2; }.recommendation-heading button[disabled],.recommendation-message button[disabled],.preference-button[disabled] { opacity:.48; }.recommendation-message { padding:18rpx; border-left:5rpx solid #87b8af; background:#edf6f3; color:#496a64; font-size:23rpx; line-height:1.55; }.recommendation-message.warning { display:flex; align-items:center; justify-content:space-between; gap:14rpx; border-left-color:#d3a34a; background:#fff8e9; color:#785716; }.recommendation-message.warning text { flex:1; }.recommendation-message.warning button { min-width:190rpx; min-height:80rpx; font-size:21rpx; }.recommendation-message.error { border-left-color:#dc766d; background:#fff0ee; color:#a33a32; }.recommendation-message.empty { border-left-color:#d3a34a; background:#fff8e9; color:#785716; }.recommendation-list { display:grid; gap:12rpx; }.recommendation-card { padding:20rpx; border:1rpx solid #c9dfda; border-radius:8rpx; background:#fff; }.recommendation-card.selected { border-color:#15998b; background:#eff9f6; box-shadow:inset 4rpx 0 #15998b; }.recommendation-card.unavailable { border-color:#d9dfdd; background:#f5f7f6; }.nurse-name { color:#173833; font-size:27rpx; font-weight:800; }.score { margin-top:6rpx; color:#627873; font-size:22rpx; }.availability { flex:none; padding:6rpx 12rpx; border-radius:999rpx; background:#dcf3ed; color:#0a7165; font-size:20rpx; font-weight:700; }.availability.unavailable { background:#ecefed; color:#687873; }.skill-row { display:flex; flex-wrap:wrap; gap:8rpx; margin-top:15rpx; }.skill-chip { padding:6rpx 11rpx; border-radius:999rpx; background:#eaf3fa; color:#2d6689; font-size:20rpx; }.recommendation-reason { margin-top:14rpx; color:#526b66; font-size:23rpx; line-height:1.6; }.preference-button { width:100%; margin-top:16rpx; background:#f7fbfa; }.preference-note { color:#6c7f7a; font-size:21rpx; line-height:1.5; }.compact { margin-top:16px; padding:16px; border-radius:6px; }.compact .recommendation-title { font-size:16px; }.compact .recommendation-subtitle { font-size:12px; }.compact .recommendation-heading button { min-width:82px; min-height:40px; padding:0 12px; font-size:12px; }.compact .recommendation-list { grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }.compact .recommendation-card { padding:14px; border-radius:6px; }.compact .nurse-name { font-size:15px; }.compact .score,.compact .recommendation-reason,.compact .preference-note { font-size:12px; }.compact .availability,.compact .skill-chip { font-size:11px; }.compact .recommendation-message { padding:12px; }.compact .preference-button { min-height:40px; font-size:12px; }
@media (max-width:900px) { .compact .recommendation-list { grid-template-columns:1fr; } }
</style>
