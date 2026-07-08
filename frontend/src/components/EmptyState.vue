<script setup lang="ts">
defineProps<{
  title: string;
  description: string;
}>();
</script>

<template>
  <view class="empty-state" aria-live="polite">
    <text class="empty-icon">▱</text>
    <view>
      <text class="empty-title">{{ title }}</text>
      <text class="empty-desc">{{ description }}</text>
    </view>
  </view>
</template>
