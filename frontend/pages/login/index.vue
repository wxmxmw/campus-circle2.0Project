<template>
  <view class="login-container">
	  <!-- 返回按钮 -->
	  <view class="nav-back" @click="goBack">
	    <text>←</text> <!-- 这里可以用 "←" 或者 "✕" -->
	  </view>
    <view class="header">
      <text class="title">欢迎来到码住校园</text>
      <text class="sub-title">登录后体验更多精彩功能</text>
    </view>

    <view class="form-box">
      <!-- 账号输入 -->
      <view class="input-item">
        <text class="label">账号</text>
        <input 
          class="input" 
          v-model="form.username" 
          placeholder="请输入学号/手机号" 
        />
      </view>
      
      <!-- 密码输入 -->
      <view class="input-item">
        <text class="label">密码</text>
        <input 
          class="input" 
          v-model="form.password" 
          password 
          placeholder="请输入密码" 
        />
      </view>

      <button class="submit-btn" :loading="loading" @click="handleLogin">
        立即登录
      </button>

      <view class="actions">
        
        <!-- 绑定跳转事件 -->
        <text class="link" @click="toggleMode">没有账号? 去注册</text>
      </view>
    </view>
    
   
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    return uni.showToast({ title: '请输入账号密码', icon: 'none' })
  }

  loading.value = true
  try {
    const res = await userApi.login({
      username: form.username,
      password: form.password
    })
    
    // 注意：这里要适配后端返回结构。
    // 如果后端返回 res.code === 200, res.data = { token: '...', user: {...} }
    // 那么这里是对的。
    if (res.code === 200) {
      // 存入 Pinia 和 Storage (假设 setLoginState 方法已在 store 中定义)
      // 如果 userStore 中没有 setLoginState，请确认 store 代码
      // 或者直接 userStore.token = res.data.token; userStore.userInfo = res.data.user;
      
      // 推荐写法：
      if (userStore.setLoginState) {
          userStore.setLoginState(res.data)
      } else {
          // 兜底写法，防止 store 没更新
          uni.setStorageSync('token', res.data.token)
          uni.setStorageSync('userInfo', res.data.user)
          // 强制刷新一下 store 状态（如果 store 是 ref 写法）
          userStore.token = res.data.token
          userStore.userInfo = res.data.user
      }
      
      uni.showToast({ title: '登录成功' })
      
      setTimeout(() => {
        // 登录成功后通常是跳回首页，或者返回上一页
        // 如果是分享链接进来的，可能没有上一页，建议用 switchTab
        uni.switchTab({ url: '/pages/index/index' })
      }, 1000)
    }
  } catch (error) {
    uni.showToast({ 
      title: error.message || '登录失败', 
      icon: 'none' 
    })
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  const pages = getCurrentPages()
  // 如果页面栈大于1，说明有上一页，直接返回
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    // 如果没有上一页（比如直接跳转过来的），回首页
    uni.switchTab({ url: '/pages/index/index' })
  }
}
// [修改] 跳转到注册页面
const toggleMode = () => {
  uni.navigateTo({ url: '/pages/login/register' })
}
</script>

<style scoped>
.login-container {
  padding: 60rpx;
  min-height: 100vh;
  background: linear-gradient(135deg, #f4f4f4 0%, #e6e6e6 100%);
}
.header { margin-top: 100rpx; margin-bottom: 80rpx; }
.title { font-size: 48rpx; font-weight: bold; display: block; margin-bottom: 20rpx; }
.sub-title { font-size: 28rpx; color: #999; }

.input-item { margin-bottom: 40rpx; border-bottom: 1rpx solid #eee; padding-bottom: 10rpx; }
.form-box {
  background: rgba(255,255,255,0.88);
  border: 1rpx solid rgba(17,17,17,0.06);
  border-radius: 28rpx;
  padding: 36rpx;
  box-shadow: 0 16rpx 40rpx rgba(17,17,17,0.06);
}
.label { font-size: 28rpx; color: #333; margin-bottom: 10rpx; display: block; font-weight: bold; }
.input { height: 80rpx; font-size: 32rpx; }

.submit-btn {
  background: #2f2f2f; color: #fff; border-radius: 50rpx; 
  margin-top: 60rpx; font-size: 32rpx; font-weight: bold;
}
.submit-btn:active { opacity: 0.9; }

.actions { display: flex; justify-content: space-between; margin-top: 30rpx; font-size: 26rpx; color: #666; }
.link { color: #5d5d5d; }

.dev-tip { margin-top: 100rpx; text-align: center; color: #ccc; font-size: 24rpx; }

.nav-back {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  /* 避开手机状态栏高度，再加一点间距 */
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
