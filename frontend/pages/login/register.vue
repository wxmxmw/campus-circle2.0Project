<template>
  <view class="register-container">
	  <view class="nav-back" @click="goBack">
	    <text>←</text> <!-- 这里可以用 "←" 或者 "✕" -->
	  </view>
    <view class="header">
      <text class="title">Create an account</text>
      <text class="sub-title">Join Campus Circle and explore campus life</text>
    </view>

    <view class="form-box">
      <!-- Username -->
      <view class="input-item">
        <text class="label">Username</text>
        <input
          class="input"
          v-model="form.username"
          placeholder="Enter a username"
        />
      </view>

      <!-- 邮箱 -->
      <view class="input-item">
        <text class="label">Campus email</text>
        <input
          class="input"
          v-model="form.email"
          placeholder="test@univ.edu.cn"
        />
      </view>

      <!-- Password -->
      <view class="input-item">
        <text class="label">Create password</text>
        <input
          class="input"
          v-model="form.password"
          password
          placeholder="Enter your password"
        />
      </view>

      <!-- Confirm password -->
      <view class="input-item">
        <text class="label">Confirm password</text>
        <input
          class="input"
          v-model="form.confirmPassword"
          password
          placeholder="Confirm your password"
        />
      </view>

      <button class="submit-btn" :loading="loading" @click="handleRegister">
        Sign up
      </button>

      <view class="actions">
        <text class="link" @click="goLogin">Already have an account? Sign in</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { userApi } from '@/api/user'

const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const handleRegister = async () => {
  // 1. 基础校验
  if (!form.username || !form.email || !form.password) {
    return uni.showToast({ title: 'Complete all registration fields', icon: 'none' })
  }
  if (form.password !== form.confirmPassword) {
    return uni.showToast({ title: 'Passwords do not match', icon: 'none' })
  }

  loading.value = true
  try {
    // 2. 调用真实 API
    const res = await userApi.register(form)

    if (res.code === 200) {
      uni.showToast({ title: 'Account created', icon: 'success' })
      // 延迟跳转回登录页
      setTimeout(() => {
        uni.navigateBack()
      }, 1500)
    }
  } catch (error) {
    // 错误已经在 request.js 中通过 toast Notice了，这里可以不做处理或打志
    console.error('Registration failed', error)
  } finally {
    loading.value = false
  }
}
const goBack = () => {
  const pages = getCurrentPages()
  // 如果页面栈大于1，说明有上一页，直接Back
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    // 如果没有上一页（比如直接跳转过来的），回首页
    uni.switchTab({ url: '/pages/index/index' })
  }
}

const goLogin = () => {
  uni.navigateBack()
}
</script>

<style scoped>
.register-container { padding: 60rpx; min-height: 100vh; background: #fff; }
.header { margin-top: 80rpx; margin-bottom: 60rpx; }
.title { font-size: 48rpx; font-weight: bold; display: block; margin-bottom: 20rpx; color: #333; }
.sub-title { font-size: 28rpx; color: #999; }

.input-item { margin-bottom: 30rpx; border-bottom: 1rpx solid #eee; padding-bottom: 10rpx; }
.label { font-size: 28rpx; color: #333; margin-bottom: 10rpx; display: block; font-weight: bold; }
.input { height: 80rpx; font-size: 32rpx; }

.submit-btn {
  background: #1890FF; /* 注册By钮用蓝色区分一下 */
  color: #fff; border-radius: 50rpx;
  margin-top: 60rpx; font-size: 32rpx; font-weight: bold;
}
.submit-btn:active { opacity: 0.9; }

.actions { display: flex; justify-content: center; margin-top: 30rpx; font-size: 28rpx; color: #666; }
.link { color: #1890FF; font-weight: bold; }
.nav-back {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  /* 避开手机Status栏高度，再加一点间距 */
  padding-top: calc(var(--status-bar-height) + 20rpx);
  padding-left: 30rpx;
  padding-right: 30rpx;
  padding-bottom: 20rpx;
}

.nav-back text {
  font-size: 44rpx; /* 图标大小 */
  color: #333;      /* 图标颜色，如果是深色背景改为 #fff */
  font-weight: bold;
}
</style>