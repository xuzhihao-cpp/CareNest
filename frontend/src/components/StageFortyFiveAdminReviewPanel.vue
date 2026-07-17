<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listAdminReviews } from '@/api/stageFortyFourToFortyEight';
import type { AdminReview } from '@/types/stageFortyFourToFortyEight';

const reviews = ref<AdminReview[]>([]);
const loading = ref(false);
const error = ref('');

const reviewerRoleLabels: Record<AdminReview['reviewerRole'], string> = {
  ELDER: '长辈',
  FAMILY: '家属'
};

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待同步';
}

async function load() {
  loading.value = true;
  error.value = '';
  const response = await listAdminReviews();
  loading.value = false;
  if (response.code === 0) reviews.value = response.data;
  else {
    reviews.value = [];
    error.value = response.code === 403 ? '当前账号没有查看服务评价的权限。' : '服务评价暂时无法读取。';
  }
}

onMounted(load);
</script>

<template>
  <view class="review-panel">
    <view class="heading">
      <view>
        <text>服务评价</text>
        <small>展示家属和长辈提交的真实评价，不需要审核</small>
      </view>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </view>
    <view v-if="error" class="notice error">{{ error }}</view>
    <view v-if="!reviews.length && !loading" class="empty">暂无服务评价。</view>
    <view v-for="review in reviews" :key="review.reviewId" class="review-row">
      <view class="review-main">
        <view class="review-title">
          <strong>{{ review.serviceName }}</strong>
          <span>{{ review.rating }} 分</span>
        </view>
        <text>{{ review.elderName }} · {{ reviewerRoleLabels[review.reviewerRole] }} · {{ review.reviewerName }}</text>
        <small>{{ formatTime(review.createdAt) }} · 订单服务评价</small>
        <view v-if="review.tags.length" class="tags">
          <text v-for="tag in review.tags" :key="tag">{{ tag }}</text>
        </view>
        <p>{{ review.content || '未填写文字评价。' }}</p>
      </view>
    </view>
  </view>
</template>

<style scoped>
.review-panel{display:grid;gap:16px;padding:20px;border:1px solid #dbe7e4;background:#fff}
.heading,.review-title{display:flex;align-items:center;justify-content:space-between;gap:14px}
.heading>view{display:grid;gap:4px}.heading text{font-size:22px;font-weight:700}.heading small,.review-main>text,.review-main>small{color:#6f817e}
.heading button{min-height:40px;margin:0;padding:0 16px;border:1px solid #bdd1cd;border-radius:6px;background:#fff;color:#146d63}
.notice,.empty{padding:14px;border-radius:6px}.error{background:#fff0ef;color:#ac3c32}.empty{background:#f3f7f6;color:#6c7d7a}
.review-row{padding:16px;border:1px solid #dce7e5;background:#fbfdfc}.review-main{display:grid;gap:8px}.review-title strong{font-size:18px;color:#1b3732}.review-title span{padding:5px 10px;border-radius:999px;background:#eaf7f4;color:#0f766e;font-weight:700}
.review-main>small{font-size:12px}.tags{display:flex;flex-wrap:wrap;gap:6px}.tags text{padding:4px 8px;border-radius:4px;background:#edf4f2;color:#55736b;font-size:12px}.review-main p{margin:2px 0 0;color:#334f48;line-height:1.6;overflow-wrap:anywhere}
</style>
