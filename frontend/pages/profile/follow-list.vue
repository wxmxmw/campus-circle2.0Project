<template>
  <view class="follow-page">
    <!-- 自定义头部 (粉色) -->
    <view class="custom-header pink-theme">
      <view class="status-bar"></view>
      <view class="nav-bar">
        <view class="back-btn" @click="goBack">
          <text class="back-arrow">←</text>
        </view>
        <text class="page-title">{{ type === 'followers' ? 'Followers' : 'Following' }}</text>
      </view>
    </view>

    <!-- 列表容器 -->
    <view class="list-container">
      <view v-for="user in list" :key="user.id" class="user-item" @click="goToUserHome(user.id)">
        <view class="avatar">
           <image 
             v-if="user.avatarUrl" 
             :src="user.avatarUrl" 
             mode="aspectFill" 
             style="width:100%;height:100%;border-radius:50%;"
           />
           <text v-else>👤</text>
        </view>
        
        <view class="info">
          <text class="name">{{ user.nickname || user.username }}</text>
          <text class="bio">{{ user.bio || 'No bio' }}</text>
        </view>
        
        <view class="btn-box" @click.stop>
          <!-- 🔥 修改处：只有登录 且 不是自己 时才显示By钮 -->
          <button 
            v-if="userStore.isLoggedIn && String(user.id) !== String(userStore.userInfo?.id)"
            class="action-btn" 
            :class="{ outline: user.isFollowing }" 
            @click="toggleFollow(user)"
          >
            {{ getBtnText(user) }}
          </button>
        </view>
      </view>
      
      <view v-if="list.length === 0" class="empty">No results</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { toAbsoluteUrl } from '@/utils/api'

const userStore = useUserStore()
const list = ref([])
const type = ref('following') 
const targetUserId = ref('')
const getImageUrl = (url) => toAbsoluteUrl(url, '/static/logo.png')

onLoad((options) => {
  if (options.type) {
    type.value = options.type
  }
  
  if (options.userId) {
    targetUserId.value = options.userId
  } else {
    targetUserId.value = userStore.userInfo?.id
  }
  
  loadData()
})

const goBack = () => uni.navigateBack()

const loadData = async () => {
  if (!targetUserId.value) return
  
  try {
    let res
    if (type.value === 'followers') {
      res = await userApi.getFollowers(targetUserId.value)
    } else {
      res = await userApi.getFollowList(targetUserId.value)
    }
    
    if (res.code === 200) {
      list.value = (res.data.list || []).map((user) => ({
        ...user,
        avatarUrl: getImageUrl(user.avatarUrl)
      }))
    }
  } catch (e) { 
    console.error(e) 
  }
}

const goToUserHome = (id) => {
  uni.navigateTo({ url: `/pages/user/home?id=${id}` })
}

const getBtnText = (user) => {
  if (user.isFollowing) {
    return type.value === 'followers' ? 'Mutual follow' : 'Following'
  }
  return type.value === 'followers' ? 'Follow back' : 'Following'
}

const toggleFollow = async (user) => {
  if (!userStore.token) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  
  const newStatus = !user.isFollowing
  try {
    await userApi.toggleFollow(user.id, newStatus)
    user.isFollowing = newStatus
    
    uni.showToast({ title: newStatus ? 'Followed' : 'Cancelled', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: 'Action failed', icon: 'none' })
  }
}
</script>

<style scoped>
.follow-page { min-height: 100vh; background: #fff; }

.custom-header { width: 100%; position: fixed; top: 0; z-index: 100; }
.status-bar { height: var(--status-bar-height); width: 100%; }

.pink-theme {
  background: linear-gradient(135deg, #FF9A9E 0%, #FECFEF 100%);
  box-shadow: 0 4rpx 10rpx rgba(255, 154, 158, 0.2);
}

.nav-bar {
  height: 80rpx;
  display: flex;
  align-items: center;
  position: relative;
  padding: 0 30rpx;
}
.back-btn {
  position: absolute;
  left: 30rpx;
  padding: 10rpx;
  z-index: 101;
}
.back-arrow { font-size: 40rpx; color: #333; font-weight: bold; }
.page-title { flex: 1; text-align: center; font-size: 34rpx; font-weight: bold; color: #333; }

.list-container {
  padding-top: calc(var(--status-bar-height) + 88rpx);
}

.user-item { display: flex; align-items: center; padding: 30rpx; border-bottom: 1rpx solid #f5f5f5; }
.avatar { width: 90rpx; height: 90rpx; background: #eee; border-radius: 50%; display: flex; justify-content: center; align-items: center; margin-right: 24rpx; font-size: 40rpx; border: 2rpx solid #fff; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.1); overflow: hidden;}
.info { flex: 1; display: flex; flex-direction: column; }
.name { font-weight: bold; font-size: 32rpx; margin-bottom: 8rpx; color: #333; }
.bio { color: #999; font-size: 26rpx; }
.action-btn { font-size: 24rpx; padding: 0 24rpx; height: 56rpx; line-height: 56rpx; border-radius: 28rpx; background: #FF758C; color: #fff; min-width: 120rpx; border: none; }
.action-btn.outline { background: #fff; color: #999; border: 1rpx solid #ddd; }
.empty { text-align: center; color: #999; margin-top: 100rpx; }
</style>
