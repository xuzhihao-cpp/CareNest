<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits<{
  (event: 'open-profile'): void;
  (event: 'back-feedback'): void;
}>();

const platformPhone = String(import.meta.env.VITE_PLATFORM_ASSISTANCE_PHONE || '').trim();
const callError = ref('');

function callPhone(phoneNumber: string, label: string) {
  callError.value = '';
  uni.makePhoneCall({
    phoneNumber,
    fail() {
      callError.value = `${label}暂时无法拨打，请使用手机电话功能手动拨号。`;
    }
  });
}
</script>

<template>
  <view class="assistance-panel">
    <view class="assistance-heading">
      <text class="assistance-kicker">及时获得帮助</text>
      <text class="assistance-title">需要协助吗？</text>
      <text class="assistance-subtitle">根据当前情况选择合适的联系方式。页面不会自动发送健康信息或代替医疗判断。</text>
    </view>

    <view class="assistance-section family-section">
      <view><text class="section-title">先联系家属</text><text class="section-help">查看档案中已经保存的紧急联系人和联系电话。</text></view>
      <button type="button" @click="emit('open-profile')">查看家属联系方式</button>
    </view>

    <view class="assistance-section platform-section">
      <view><text class="section-title">平台协助</text><text class="section-help">需要服务协调或使用帮助时，可以联系平台。</text></view>
      <button v-if="platformPhone" type="button" @click="callPhone(platformPhone, '平台协助热线')">拨打平台协助热线</button>
      <text v-else class="unavailable-note">当前部署环境尚未配置平台协助热线，请先联系家属。</text>
    </view>

    <view class="assistance-section emergency-section">
      <view><text class="section-title">紧急医疗情况</text><text class="section-help">如出现意识不清、呼吸困难、持续胸痛或其他危急情况，请立即拨打急救电话。</text></view>
      <button type="button" @click="callPhone('120', '急救电话')">拨打 120</button>
    </view>

    <view v-if="callError" class="call-error" role="alert">{{ callError }}</view>
    <button class="back-command" type="button" @click="emit('back-feedback')">返回健康反馈</button>
  </view>
</template>

<style scoped>
.assistance-panel { display:grid; gap:20rpx; min-width:0; color:#17312e; }
.assistance-heading { display:grid; gap:7rpx; padding:12rpx 4rpx 4rpx; }
.assistance-heading text,.assistance-section text { display:block; }
.assistance-kicker { color:#0f766e; font-size:22rpx; font-weight:800; }
.assistance-title { font-size:38rpx; font-weight:850; }
.assistance-subtitle { color:#637974; font-size:24rpx; line-height:1.55; }
.assistance-section { display:grid; gap:18rpx; padding:24rpx; border:1rpx solid #d7e3e0; border-left-width:7rpx; background:#fff; }
.assistance-section>view { display:grid; gap:7rpx; }
.family-section { border-left-color:#4b91b5; }
.platform-section { border-left-color:#168c81; }
.emergency-section { border-color:#e3aaa5; border-left-color:#c7433b; background:#fff7f6; }
.section-title { font-size:29rpx; font-weight:850; }
.section-help { color:#607671; font-size:23rpx; line-height:1.55; }
.assistance-section button,.back-command { min-height:78rpx; margin:0; border:1rpx solid #b8d0cb; border-radius:4rpx; background:#fff; color:#176d65; font-size:26rpx; font-weight:800; }
.platform-section button { border-color:#167f76; background:#167f76; color:#fff; }
.emergency-section button { border-color:#bd3e36; background:#bd3e36; color:#fff; }
.unavailable-note { padding:16rpx; background:#f4f6f5; color:#667873; font-size:22rpx; line-height:1.5; }
.call-error { padding:18rpx 20rpx; border:1rpx solid #efb7b2; background:#fff2f1; color:#a3342e; font-size:23rpx; line-height:1.55; }
.back-command { width:100%; }
</style>
