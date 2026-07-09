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
        <text class="login-mark">✦</text>
        <view>
          <text class="eyebrow">CareNest Auth</text>
          <text class="login-title">登录智慧护理平台</text>
          <text class="login-subtitle">四类演示账号 · 角色菜单返回后进入对应首页</text>
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

      <view class="auth-contract glass-panel">
        <view class="section-title">
          <text>▥</text>
          <text>阶段2接口契约</text>
        </view>
        <text>POST /api/v1/auth/login</text>
        <text>POST /api/v1/auth/logout</text>
        <text>GET /api/v1/auth/me</text>
        <text>GET /api/v1/auth/menus</text>
      </view>

      <view class="stage-strip compact" aria-label="当前角色">
        <text>{{ activeRole }}</text>
        <text>{{ selectedAccount.username }}</text>
      </view>

      <view v-if="message" class="error-banner" role="alert">
        <text>{{ message }}</text>
      </view>
    </view>
  </view>
</template>
