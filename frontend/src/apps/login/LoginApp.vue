<script setup lang="ts">
import { computed, ref } from 'vue';
import { getDemoAccounts, getRoleHomePath, login } from '@/api/stageTwo';
import type { DemoAccount } from '@/types/stageTwo';

const accounts = getDemoAccounts();
const selectedAccount = ref<DemoAccount>(accounts[0]);
const username = ref(selectedAccount.value.username);
const password = ref(selectedAccount.value.password);
const message = ref('');
const loading = ref(false);

const activeRole = computed(() => selectedAccount.value.roles[0]);

function chooseAccount(account: DemoAccount) {
  selectedAccount.value = account;
  username.value = account.username;
  password.value = account.password;
  message.value = '';
}

async function submitLogin() {
  loading.value = true;
  message.value = '';

  const response = await login({
    username: username.value.trim(),
    password: password.value
  });

  loading.value = false;

  if (response.code !== 0) {
    message.value = `${response.code} ${response.message}`;
    return;
  }

  const path = getRoleHomePath(response.data.roles[0], response.data.menus);
  uni.reLaunch({ url: path });
}

function fillWrongPassword() {
  password.value = 'wrong-password';
  message.value = '';
}
</script>

<template>
  <view class="login-shell">
    <view class="login-card glass-panel">
      <view class="login-head">
        <text class="login-mark">C</text>
        <view>
          <text class="eyebrow">智慧护理平台</text>
          <text class="login-title">登录智慧护理平台</text>
          <text class="login-subtitle">选择角色后登录，进入对应工作台。</text>
        </view>
      </view>

      <view class="account-grid" aria-label="演示账号">
        <button
          v-for="account in accounts"
          :key="account.username"
          class="account-chip"
          :class="{ active: account.username === selectedAccount.username }"
          type="button"
          @click="chooseAccount(account)"
        >
          <text>{{ account.roles[0] }}</text>
          <text>{{ account.displayName }}</text>
        </button>
      </view>

      <view class="form-panel">
        <label class="field">
          <text>用户名</text>
          <input v-model="username" class="input" name="username" placeholder="username" type="text" />
        </label>
        <label class="field">
          <text>密码</text>
          <input v-model="password" class="input" name="password" password placeholder="password" />
        </label>
      </view>

      <view class="login-actions">
        <button class="hero-action login-submit" type="button" :disabled="loading" @click="submitLogin">
          <text>{{ loading ? '登录中' : '登录' }}</text>
          <text>›</text>
        </button>
        <button class="ghost-action" type="button" @click="fillWrongPassword">
          <text>测试错误密码</text>
        </button>
      </view>

      <view class="login-role"><text>{{ activeRole }}</text><text>{{ selectedAccount.username }}</text></view>

      <view v-if="message" class="error-banner" role="alert">
        <text>{{ message }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.login-shell { min-height:100vh; display:grid; place-items:center; padding:24px; box-sizing:border-box; background:#eff5f3; }.login-card { width:min(100%,420px); padding:28px; border:1px solid #d9e6e2; border-radius:10px; background:#fff; box-shadow:0 18px 48px rgba(23,65,58,.09); }.login-head { display:flex; align-items:center; gap:14px; margin-bottom:24px; }.login-mark { display:grid; place-items:center; width:42px; height:42px; border-radius:8px; background:#147d72; color:#fff; font-size:22px; font-weight:700; }.login-title,.login-subtitle { display:block; }.login-title { margin-top:4px; color:#17352f; font-size:24px; font-weight:700; }.login-subtitle { margin-top:6px; color:#71837e; font-size:13px; }.account-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; }.account-chip { display:grid; gap:3px; margin:0; padding:12px; border:1px solid #dce7e4; border-radius:7px; background:#fbfdfc; color:#536965; text-align:left; font-size:12px; }.account-chip text:first-child { color:#147d72; font-weight:700; }.account-chip.active { border-color:#147d72; background:#e8f6f2; }.form-panel { display:grid; gap:14px; margin-top:20px; }.field > text { color:#536965; font-size:13px; }.input { box-sizing:border-box; width:100%; margin-top:7px; border:1px solid #d7e3df; border-radius:6px; padding:11px; background:#fff; }.login-actions { display:grid; grid-template-columns:1fr auto; gap:9px; margin-top:20px; }.login-submit { margin:0; border:0; border-radius:6px; background:#147d72; color:#fff; }.ghost-action { margin:0; border:1px solid #d7e3df; border-radius:6px; background:#fff; color:#58706a; }.login-role { display:flex; justify-content:space-between; margin-top:18px; color:#6e807b; font-size:12px; }.error-banner { margin-top:14px; padding:10px; border-radius:6px; background:#fff0ef; color:#a53b31; }
</style>
