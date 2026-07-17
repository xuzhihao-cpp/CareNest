<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getRecommendedTrainingArticles, markTrainingArticleRead } from '@/api/stageFortyNineToFiftyFive';
import type { RecommendedTrainingArticle } from '@/types/stageFortyNineToFiftyFive';
import { resolveTrainingArticleUrl } from '@/utils/trainingArticleUrl';

const props = defineProps<{ orderId: string; serviceName?: string }>();
const emit = defineEmits<{ (event: 'close'): void; (event: 'reading-state', complete: boolean): void }>();
const records = ref<RecommendedTrainingArticle[]>([]);
const loading = ref(false);
const error = ref('');
const workingId = ref('');
const openedAt = new Map<string, number>();
const openedIds = ref<string[]>([]);
let controller: AbortController | null = null;
let sequence = 0;
const requiredPending = computed(() => records.value.filter((item) => item.requiredRead && item.readStatus === 'UNREAD'));

function notifyState() { emit('reading-state', requiredPending.value.length === 0); }
function readLabel(value: string) { return value === 'CONFIRMED' ? '已确认' : value === 'READ' ? '已阅读' : '未阅读'; }

async function load() {
  const orderId = props.orderId.trim();
  controller?.abort();
  controller = new AbortController();
  const current = ++sequence;
  records.value = [];
  error.value = '';
  loading.value = true;
  if (!orderId) { loading.value = false; error.value = '请选择需要准备的服务任务。'; return; }
  const response = await getRecommendedTrainingArticles(orderId, controller.signal);
  if (current !== sequence) return;
  loading.value = false;
  if (response.code === 499) return;
  if (response.code !== 0) { error.value = response.message || '学习资料暂时无法读取。'; notifyState(); return; }
  records.value = response.data;
  notifyState();
}

function openArticle(article: RecommendedTrainingArticle) {
  openedAt.set(article.articleId, Date.now());
  if (!openedIds.value.includes(article.articleId)) openedIds.value = [...openedIds.value, article.articleId];
  if (!article.contentUrl) return;
  try {
    const target = resolveTrainingArticleUrl(article.contentUrl, window.location.origin);
    window.open(target, '_blank', 'noopener,noreferrer');
  } catch {
    error.value = '文章地址无效，请联系平台维护人员。';
  }
}

async function markRead(article: RecommendedTrainingArticle) {
  if (workingId.value || article.readStatus !== 'UNREAD') return;
  if (article.requiredRead && (!article.contentUrl || !openedIds.value.includes(article.articleId))) {
    error.value = article.contentUrl ? '请先打开并阅读这份必读资料。' : '必读资料暂时无法打开，请联系平台维护人员。';
    return;
  }
  workingId.value = article.articleId;
  error.value = '';
  const seconds = Math.max(1, Math.min(86400, Math.round((Date.now() - (openedAt.get(article.articleId) ?? Date.now())) / 1000)));
  const response = await markTrainingArticleRead(article.articleId, props.orderId, seconds);
  workingId.value = '';
  if (response.code !== 0) { error.value = response.message || '阅读状态暂时无法保存。'; return; }
  const current = records.value.find((item) => item.articleId === article.articleId);
  if (current) current.readStatus = response.data.readStatus;
  notifyState();
}

watch(() => props.orderId, load, { immediate: true });
onBeforeUnmount(() => { sequence += 1; controller?.abort(); });
</script>

<template>
  <view class="reading-panel">
    <view class="panel-head"><view><text class="eyebrow">服务前学习</text><text class="title">推荐学习资料</text><text class="subtitle">{{ serviceName || '当前护理服务' }} · 完成必读资料后再开始服务</text></view><button type="button" class="secondary" @click="emit('close')">返回任务</button></view>
    <view v-if="requiredPending.length" class="warning">还有 {{ requiredPending.length }} 份必读资料未完成，请阅读后确认。</view>
    <view v-if="loading" class="state">正在读取推荐资料...</view>
    <view v-else-if="error" class="state error">{{ error }}<button type="button" @click="load">重新读取</button></view>
    <view v-else-if="!records.length" class="state">当前服务暂无推荐学习资料，可以继续服务准备。</view>
    <view v-else class="article-list">
      <article v-for="article in records" :key="article.articleId" class="article-card">
        <view class="article-head"><view><strong>{{ article.title }}</strong><text v-if="article.summary">{{ article.summary }}</text></view><view class="chips"><text v-if="article.requiredRead" class="required">必读</text><text class="read-status" :class="article.readStatus.toLowerCase()">{{ readLabel(article.readStatus) }}</text></view></view>
        <view class="actions"><button v-if="article.contentUrl" type="button" class="secondary" @click="openArticle(article)">打开文章</button><button v-if="article.readStatus === 'UNREAD'" type="button" class="primary" :disabled="workingId === article.articleId || (article.requiredRead && (!article.contentUrl || !openedIds.includes(article.articleId)))" @click="markRead(article)">{{ workingId === article.articleId ? '保存中...' : '确认已阅读' }}</button></view>
      </article>
    </view>
  </view>
</template>

<style scoped>
.reading-panel{padding:24rpx;border:1rpx solid #d9e6e2;background:#fff;color:#18312d}.panel-head,.article-head,.actions{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx}.eyebrow,.title,.subtitle{display:block}.eyebrow{color:#147d72;font-size:20rpx;font-weight:700}.title{margin-top:8rpx;font-size:36rpx;font-weight:700}.subtitle{margin-top:8rpx;color:#6b7e79;font-size:23rpx}.warning,.state{margin-top:24rpx;padding:20rpx;border-left:6rpx solid #d79a2f;background:#fff8e7;color:#765513;font-size:24rpx}.state{border-color:#b7cbc5;background:#f4f8f6;color:#667974;text-align:center}.state.error{border-color:#d96c61;background:#fff2f0;color:#a23c33}.state button{display:block;min-height:68rpx;margin:16rpx auto 0;padding:0 20rpx;border:1rpx solid #bdcec9;border-radius:6rpx;background:#fff;color:#176f66;font-size:23rpx}.article-list{display:grid;gap:16rpx;margin-top:22rpx}.article-card{padding:22rpx;border:1rpx solid #dce7e4;border-radius:8rpx;background:#fbfdfc}.article-head view:first-child{min-width:0}.article-head strong,.article-head text{display:block}.article-head strong{font-size:28rpx}.article-head view:first-child text{margin-top:8rpx;color:#6d7e79;font-size:23rpx;line-height:1.5}.chips{display:flex;gap:8rpx;flex:none}.chips text{padding:6rpx 10rpx;border-radius:5rpx;font-size:20rpx}.required{background:#fff0d5;color:#9b6715}.read-status{background:#eef2f0;color:#61726d}.read-status.read,.read-status.confirmed{background:#dff4ed;color:#08776c}.actions{justify-content:flex-end;margin-top:18rpx}.primary,.secondary{display:inline-flex;align-items:center;justify-content:center;min-height:72rpx;margin:0;padding:0 22rpx;border-radius:6rpx;font-size:23rpx}.primary{border:1rpx solid #147d72;background:#147d72;color:#fff}.secondary{border:1rpx solid #bed0cb;background:#fff;color:#20685f}
</style>
