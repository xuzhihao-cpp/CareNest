<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getServiceItems } from '@/api/stageEight';
import {
  changeTrainingArticleStatus,
  createTrainingArticle,
  getTrainingArticles,
  updateTrainingArticle
} from '@/api/stageFortyNineToFiftyFive';
import type { ServiceItemResponse } from '@/types/stageEight';
import type { TrainingArticle, TrainingArticleInput, TrainingArticleStatus } from '@/types/stageFortyNineToFiftyFive';

const articles = ref<TrainingArticle[]>([]);
const services = ref<ServiceItemResponse[]>([]);
const selectedId = ref('');
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const notice = ref('');
const form = ref({
  title: '', summary: '', contentUrl: '', tagsText: '', riskTagsText: '',
  serviceIds: [] as string[], requiredRead: false
});

const selected = computed(() => articles.value.find((item) => item.articleId === selectedId.value) ?? null);
const statusLabels: Record<TrainingArticleStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' };

function splitValues(value: string) {
  return Array.from(new Set(value.split(/[，,]/).map((item) => item.trim()).filter(Boolean)));
}

function resetEditor() {
  selectedId.value = '';
  form.value = { title: '', summary: '', contentUrl: '', tagsText: '', riskTagsText: '', serviceIds: [], requiredRead: false };
  error.value = '';
  notice.value = '';
}

function editArticle(article: TrainingArticle) {
  selectedId.value = article.articleId;
  form.value = {
    title: article.title,
    summary: article.summary,
    contentUrl: article.contentUrl,
    tagsText: article.tags.join('，'),
    riskTagsText: article.riskTags.join('，'),
    serviceIds: [...article.serviceIds],
    requiredRead: article.requiredRead
  };
  error.value = '';
  notice.value = '';
}

function toggleService(serviceId: string) {
  form.value.serviceIds = form.value.serviceIds.includes(serviceId)
    ? form.value.serviceIds.filter((item) => item !== serviceId)
    : [...form.value.serviceIds, serviceId];
}

function payload(status: TrainingArticleStatus): TrainingArticleInput {
  return {
    title: form.value.title.trim(), summary: form.value.summary.trim(), contentUrl: form.value.contentUrl.trim(),
    tags: splitValues(form.value.tagsText), serviceIds: [...form.value.serviceIds],
    riskTags: splitValues(form.value.riskTagsText), requiredRead: form.value.requiredRead, status
  };
}

function validate() {
  if (form.value.title.trim().length < 2 || form.value.title.trim().length > 128) return '文章标题需填写 2 至 128 个字符。';
  if (form.value.summary.trim().length > 500) return '文章摘要不能超过 500 个字符。';
  if (form.value.contentUrl.trim().length > 255) return '内容地址过长，请检查后重试。';
  if (splitValues(form.value.tagsText).length > 30 || splitValues(form.value.riskTagsText).length > 30) return '标签数量不能超过 30 个。';
  return '';
}

async function load() {
  loading.value = true;
  error.value = '';
  const [articleResponse, serviceResponse] = await Promise.all([getTrainingArticles(), getServiceItems('normal', true)]);
  if (articleResponse.code === 0) {
    articles.value = articleResponse.data;
  }
  else error.value = articleResponse.message || '培训文章暂时无法读取。';
  if (serviceResponse.code === 0) services.value = serviceResponse.data.records;
  loading.value = false;
}

async function saveDraft() {
  if (saving.value) return;
  const invalid = validate();
  if (invalid) { error.value = invalid; return; }
  saving.value = true;
  error.value = '';
  const response = selected.value
    ? await updateTrainingArticle(selected.value.articleId, payload('DRAFT'))
    : await createTrainingArticle(payload('DRAFT'));
  saving.value = false;
  if (response.code !== 0) { error.value = response.message || '文章暂时无法保存。'; return; }
  notice.value = '草稿已保存。';
  await load();
  selectedId.value = response.data.articleId;
}

async function changeStatus(article: TrainingArticle, status: 'PUBLISHED' | 'OFFLINE') {
  if (saving.value) return;
  saving.value = true;
  error.value = '';
  const source = selectedId.value === article.articleId ? payload(status) : { ...article, status };
  const response = await changeTrainingArticleStatus(article.articleId, source);
  saving.value = false;
  if (response.code !== 0) { error.value = response.message || '文章状态暂时无法更新。'; return; }
  notice.value = status === 'PUBLISHED' ? '文章已发布。' : '文章已下线，不再参与推荐。';
  await load();
}

onMounted(load);
</script>

<template>
  <view class="article-workbench">
    <view class="workbench-head">
      <view><text class="eyebrow">内容运营</text><text class="title">培训文章管理</text><text class="subtitle">维护服务前学习资料，并设置推荐范围与必读要求。</text></view>
      <button type="button" class="secondary" @click="load">刷新</button>
    </view>
    <view v-if="error" class="message error">{{ error }}</view>
    <view v-if="notice" class="message success">{{ notice }}</view>
    <view class="layout">
      <section class="list-pane">
        <view class="section-head"><strong>文章列表</strong><button type="button" class="text-button" @click="resetEditor">新建文章</button></view>
        <view v-if="loading" class="empty">正在读取培训文章...</view>
        <button v-for="(article, index) in articles" :key="article.articleId" type="button" class="article-row" :class="{ selected: selectedId === article.articleId }" @click="editArticle(article)">
          <view><strong>{{ article.title || `培训文章 ${index + 1}` }}</strong><text>{{ article.summary || '文章详情将在编辑区维护' }}</text></view>
          <text class="status" :class="article.status.toLowerCase()">{{ statusLabels[article.status] }}</text>
        </button>
        <view v-if="!loading && !articles.length" class="empty">暂无培训文章，可从右侧新建草稿。</view>
      </section>
      <section class="editor-pane">
        <view class="section-head"><strong>{{ selected ? '编辑文章' : '新建文章' }}</strong><text>{{ selected ? statusLabels[selected.status] : '草稿' }}</text></view>
        <label>文章标题 *</label><input v-model="form.title" class="form-input" maxlength="128" placeholder="例如：上门护理前的安全准备" />
        <label>文章摘要</label><textarea v-model="form.summary" class="form-textarea" maxlength="500" placeholder="用简短文字说明学习重点" />
        <label>内容地址</label><input v-model="form.contentUrl" class="form-input" maxlength="255" placeholder="填写平台可访问的文章地址" />
        <label>内容标签</label><input v-model="form.tagsText" class="form-input" placeholder="多个标签用逗号分隔" />
        <label>风险标签</label><input v-model="form.riskTagsText" class="form-input" placeholder="例如：跌倒风险，夜间照护" />
        <label>适用服务</label>
        <view class="choice-grid"><button v-for="service in services" :key="service.serviceId" type="button" :class="{ active: form.serviceIds.includes(service.serviceId) }" @click="toggleService(service.serviceId)">{{ service.serviceName }}</button><text v-if="!services.length" class="muted">服务项目暂时无法读取，可先保存通用文章。</text></view>
        <label class="check-row"><checkbox :checked="form.requiredRead" @click="form.requiredRead = !form.requiredRead" /><text>护理人员服务前必须阅读</text></label>
        <view class="actions">
          <button type="button" class="primary" :disabled="saving" @click="saveDraft">{{ saving ? '保存中...' : '保存草稿' }}</button>
          <button v-if="selected && selected.status !== 'PUBLISHED'" type="button" class="secondary" :disabled="saving" @click="changeStatus(selected, 'PUBLISHED')">发布</button>
          <button v-if="selected && selected.status === 'PUBLISHED'" type="button" class="danger" :disabled="saving" @click="changeStatus(selected, 'OFFLINE')">下线</button>
        </view>
      </section>
    </view>
  </view>
</template>

<style scoped>
.article-workbench{padding:24px;border:1px solid #dce7e4;background:#fff;color:#17312e}.workbench-head,.section-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.eyebrow,.title,.subtitle{display:block}.eyebrow{font-size:12px;font-weight:700;color:#147d72}.title{margin-top:6px;font-size:25px;font-weight:700}.subtitle{margin-top:7px;color:#687b76;font-size:14px}.layout{display:grid;grid-template-columns:minmax(280px,.8fr) minmax(420px,1.2fr);gap:24px;margin-top:24px}.list-pane{border-right:1px solid #e1e9e6;padding-right:24px}.section-head{margin-bottom:16px}.section-head strong{font-size:18px}.section-head text{color:#6e807b;font-size:13px}.article-row{display:flex;align-items:center;justify-content:space-between;width:100%;min-height:84px;margin:0 0 10px;padding:16px;border:1px solid #dce7e4;border-radius:6px;background:#fff;text-align:left}.article-row.selected{border-color:#2b9a8e;background:#edf8f5}.article-row view{min-width:0}.article-row strong,.article-row text{display:block}.article-row strong{color:#17312e;font-size:15px}.article-row view text{margin-top:6px;color:#71827d;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.status{flex:none;margin-left:12px;padding:5px 9px;border-radius:4px;background:#edf1ef;color:#5e6f6a;font-size:12px}.status.published{background:#def3ec;color:#08786d}.status.offline{background:#fff0dd;color:#9b6419}.editor-pane{display:grid;grid-template-columns:1fr 1fr;gap:10px 14px}.editor-pane label{grid-column:1/-1;margin-top:4px;color:#405c55;font-size:13px;font-weight:700}.form-input,.form-textarea{grid-column:1/-1;box-sizing:border-box;width:100%;border:1px solid #cfdcd8;border-radius:5px;background:#fbfdfc;font-size:14px}.form-input{display:flex;align-items:center;min-height:48px;padding:0 14px}.form-textarea{height:92px;padding:12px 14px}.editor-pane :deep(.form-input .uni-input-wrapper){display:flex;align-items:center;width:100%;height:46px!important;min-height:46px!important}.editor-pane :deep(.form-input .uni-input-input){display:block;width:100%;height:46px!important;min-height:46px!important;line-height:46px!important;color:#17312e;font-size:14px;cursor:text}.editor-pane :deep(.form-input .uni-input-placeholder){height:46px!important;line-height:46px!important;color:#84938f}.choice-grid{grid-column:1/-1;display:flex;flex-wrap:wrap;gap:8px}.choice-grid button{min-height:38px;margin:0;padding:0 13px;border:1px solid #c9dad5;border-radius:5px;background:#fff;color:#416159;font-size:13px}.choice-grid button.active{border-color:#2a988c;background:#e6f5f1;color:#08786d;font-weight:700}.check-row{display:flex!important;align-items:center;gap:8px}.actions{grid-column:1/-1;display:flex;gap:10px;margin-top:12px}.primary,.secondary,.danger,.text-button{display:inline-flex;align-items:center;justify-content:center;min-height:42px;margin:0;padding:0 16px;border-radius:5px;font-size:14px}.primary{border:1px solid #147d72;background:#147d72;color:#fff}.secondary{border:1px solid #bcd0ca;background:#fff;color:#245f56}.danger{border:1px solid #e2aaa5;background:#fff;color:#b34036}.text-button{border:0;background:transparent;color:#147d72}.message,.empty{margin-top:16px;padding:13px 15px;border-left:4px solid #b8c9c4;background:#f4f8f6;color:#60736e}.message.error{border-color:#d96e62;background:#fff2f0;color:#a63b32}.message.success{border-color:#2b9a8e;background:#edf8f5;color:#147d72}.muted{color:#73847f;font-size:13px}@media(min-width:901px){.list-pane{position:sticky;top:24px;align-self:start;max-height:calc(100vh - 180px);overflow-y:auto;scrollbar-gutter:stable}.list-pane .section-head{position:sticky;top:0;z-index:2;margin-bottom:12px;padding:0 0 12px;background:#fff}}@media(max-width:900px){.layout{grid-template-columns:1fr}.list-pane{border-right:0;border-bottom:1px solid #e1e9e6;padding:0 0 18px}.article-workbench{padding:18px}.workbench-head{align-items:flex-start}}
</style>
