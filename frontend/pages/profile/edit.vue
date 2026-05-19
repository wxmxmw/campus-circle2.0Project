<template>
  <view class="edit-container">
    <!-- 新增：自定义顶部导航栏 -->
    <view class="custom-header">
      <!-- 状态栏占位 -->
      <view class="status-bar"></view>
      <!-- 导航内容 -->
      <view class="nav-bar">
        <view class="back-btn" @click="goBack">
          <text class="back-icon">←</text>
        </view>
        <text class="page-title">编辑资料</text>
        <!-- 右侧占位，保证标题居中 -->
        <view class="right-placeholder"></view>
      </view>
    </view>

    <!-- 表单区域 -->
    <view class="form-list">
      <!-- 头像行 -->
      <view class="form-item avatar-row" @click="handleChooseAvatar">
        <text class="label">头像</text>
        <view class="right">
          <image 
            :src="avatarPreviewUrl"
            class="avatar-preview" 
            mode="aspectFill"
          />
          <text class="arrow">></text>
        </view>
      </view>

      <!-- 昵称 -->
      <view class="form-item">
        <text class="label">昵称</text>
        <input class="input" v-model="form.nickname" placeholder="请输入昵称" />
      </view>

      <!-- 学院 -->
      <view class="form-item">
        <text class="label">学院</text>
        <input class="input" v-model="form.school" placeholder="例如：计算机学院" />
      </view>

      <!-- 签名 -->
      <view class="form-item">
        <text class="label">个性签名</text>
        <input class="input" v-model="form.bio" placeholder="介绍一下自己吧" />
      </view>
    </view>

    <button class="save-btn" @click="handleSave" :loading="loading">保存修改</button>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'
import { commonApi } from '@/api/common'
import { toAbsoluteUrl } from '@/utils/api'

const userStore = useUserStore()
const loading = ref(false)

// 表单数据初始化
const form = reactive({
  avatarUrl: '',
  nickname: '',
  school: '',
  bio: ''
})

const avatarPreviewUrl = computed(() => toAbsoluteUrl(form.avatarUrl, '/static/default-avatar.png'))

onMounted(() => {
  // 回显当前数据
  const info = userStore.userInfo
  if (info) {
    form.avatarUrl = info.avatarUrl
    form.nickname = info.nickname
    form.school = info.school
    form.bio = info.bio
  }
})

// 选择并上传头像
const handleChooseAvatar = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const tempFilePath = res.tempFilePaths[0]
      // 先在本地显示预览（优化体验）
      form.avatarUrl = tempFilePath
      
      try {
        uni.showLoading({ title: '上传中...' })
        // 调用上传 API
        const uploadRes = await commonApi.uploadImage(tempFilePath)
        if (uploadRes.code === 200) {
          form.avatarUrl = uploadRes.data.url
        }
      } catch (error) {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
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

// 保存资料
const handleSave = async () => {  
  if (!form.nickname) return uni.showToast({ title: '昵称不能为空', icon: 'none' })  
    
  loading.value = true  
  try {  
    const payload = {  
      avatarUrl: (form.avatarUrl || '').trim(),  
      nickname: (form.nickname || '').trim(),  
      school: (form.school || '').trim(),  
      bio: (form.bio || '').trim()  
    }  
    console.log('【保存数据】', payload)  
    const res = await userApi.updateProfile(payload)  
    console.log('【保存响应】', res)  
      
    if (res.code === 200) {  
      userStore.updateUserInfo(res.data || payload)
      uni.showToast({ title: '保存成功' })  
      setTimeout(() => uni.navigateBack(), 1000)  
    }  
  } catch (error) {  
    console.error('【保存失败】', error)  
    uni.showToast({ title: error.message || '保存失败', icon: 'none' })  
  } finally {  
    loading.value = false  // 确保loading状态被重置  
  }  
}
</script>

<style scoped>
/* 容器 */
.edit-container { 
  min-height: 100vh; 
  background: #F5F5F5; 
  /* 关键点：避开顶部导航栏的高度
     状态栏高度 + 导航栏内容高度(88rpx) + 额外间距(20rpx) 
  */
  padding-top: calc(var(--status-bar-height) + 88rpx + 20rpx);
}

/* --- 自定义导航栏样式 --- */
.custom-header {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  background: #fff; /* 白色背景，与灰色页面区分 */
  z-index: 999;
  box-shadow: 0 1rpx 6rpx rgba(0,0,0,0.05); /* 加一点阴影更有层次感 */
}
.status-bar {
  height: var(--status-bar-height); /* 占满手机状态栏 */
  background: #fff;
}
.nav-bar {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20rpx;
}
.back-btn {
  width: 80rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start; /* 靠左 */
}
.back-icon {
  font-size: 40rpx;
  color: #333;
  font-weight: bold;
}
.page-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #333;
}
.right-placeholder {
  width: 80rpx; /* 与返回按钮同宽，确保标题绝对居中 */
}

/* --- 表单样式 --- */
.form-list { background: #fff; padding: 0 30rpx; }
.form-list {
  margin: 0 20rpx;
  border-radius: 24rpx;
  border: 1rpx solid rgba(17,17,17,0.06);
  box-shadow: 0 12rpx 30rpx rgba(17,17,17,0.06);
}
.form-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 30rpx 0; border-bottom: 1rpx solid #eee;
}
.form-item:last-child { border-bottom: none; } /* 去掉最后一行的线 */

.label { font-size: 30rpx; color: #333; width: 160rpx; }
.input { flex: 1; text-align: right; font-size: 30rpx; color: #333; }
.right { display: flex; align-items: center; }
.avatar-preview { width: 100rpx; height: 100rpx; border-radius: 50%; margin-right: 20rpx; background: #eee; }
.arrow { color: #ccc; }

.save-btn {
  margin: 60rpx 30rpx; background: #2f2f2f; color: #fff; 
  border-radius: 40rpx; font-size: 32rpx;
}
.save-btn:active { opacity: 0.9; }
</style>
