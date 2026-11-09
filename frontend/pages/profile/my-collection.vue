<template>
  <view class="container">
    <!-- 自定义导航 -->
    <view class="nav-header">
      <view class="back-btn" @click="goBack">←</view>
      <text class="title">Saved posts</text>
    </view>
    
    <!-- 列表内容 -->
    <scroll-view scroll-y class="list-container" @scrolltolower="loadMore">
      <!-- Loading且列表为空 -->
      <view v-if="loading && list.length === 0" class="loading-state">Loading...</view>
      
      <!-- 空Status -->
      <view v-else-if="!loading && list.length === 0" class="empty-state">
        <text>No saved posts</text>
      </view>

      <!-- 列表项 -->
      <view 
        v-else 
        class="post-item" 
        v-for="item in list" 
        :key="item.id"
        @click="goToDetail(item.id)"
      >
        <view class="post-header">
          <!-- 这里的 item.author 可能为 null，加个判断防止报错 -->
          <image :src="item.author?.avatar || '/static/logo.png'" class="avatar" mode="aspectFill" />
          <text class="nickname">{{ item.author?.nickname || 'Unknown user' }}</text>
          <text class="time">{{ formatTime(item.createTime) }}</text>
        </view>
        
        <view class="post-content">{{ item.content }}</view>
        
        <view class="post-images" v-if="item.images && item.images.length">
          <image :src="item.images[0]" mode="aspectFill" class="post-img" />
          <view v-if="item.images.length > 1" class="img-count">+{{item.images.length}}</view>
        </view>
        
        <view class="post-footer">
          <text>Views {{ item.stats?.views || 0 }}</text>
          <text>Saved on {{ formatTime(item.collectedAt) }}</text>
        </view>
      </view>
      
      <!-- 底部Status -->
      <view v-if="loading && list.length > 0" class="loading-more">Loading more...</view>
      <view v-if="!hasMore && list.length > 0" class="no-more">No more items</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { toAbsoluteUrl } from '@/utils/api'

const list = ref([])
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const getImageUrl = (url) => toAbsoluteUrl(url, '/static/logo.png')

onMounted(() => {
  fetchData()
})

const fetchData = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  
  try {
    console.log('Started请求Saved posts，页码:', page.value)
    
    // 调用 API
    const res = await userApi.getMyCollections({ page: page.value, size: 10 })
    
    console.log('收藏列表Back:', res) // 调试点 1

    if (res.code === 200) {
      const newItems = (res.data.list || []).map((item) => ({
        ...item,
        author: item.author
          ? {
              ...item.author,
              avatar: getImageUrl(item.author.avatar || item.author.avatarUrl)
            }
          : item.author,
        images: Array.isArray(item.images) ? item.images.map(getImageUrl) : []
      }))
      
      if (page.value === 1) {
        list.value = newItems
      } else {
        list.value = [...list.value, ...newItems]
      }
      
      // 判断是否还有更多
      if (newItems.length < 10 || list.value.length >= res.data.total) {
        hasMore.value = false
      } else {
        page.value++
      }
    } else {
      uni.showToast({ title: res.message || 'Could not load', icon: 'none' })
    }
  } catch (e) {
    console.error('获取收藏列表报错:', e) // 调试点 2: 这里能看到是 JS 错误还是网络错误
    uni.showToast({ title: 'Loading failed', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  fetchData()
}

const goBack = () => {
  uni.navigateBack()
}

const goToDetail = (id) => {
  // 确保 id 存在
  if(id) {
      uni.navigateTo({ url: `/pages/post/detail?id=${id}` })
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  // 处理可能的时间格式
  try {
      return timeStr.split('T')[0]
  } catch(e) {
      return timeStr
  }
}
</script>

<style scoped>
.container { 
    background: #f5f5f5; 
    min-height: 100vh; 
    /* 关键：给 padding-top 留出自定义导航的高度 + Status栏高度 */
    padding-top: calc(100rpx + var(--status-bar-height));
}

.nav-header {
  position: fixed; 
  top: 0; 
  left: 0; 
  right: 0; 
  height: 100rpx;
  background: #fff; 
  z-index: 100; 
  display: flex; 
  align-items: center; 
  padding: 0 30rpx;
  box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.05);
  /* 关键：适配刘海屏，让内容往下移 */
  padding-top: var(--status-bar-height);
}

.back-btn { font-size: 40rpx; padding: 20rpx; margin-right: 20rpx; }
.title { font-size: 34rpx; font-weight: bold; }

.list-container { 
    /* 关键：设置高度让 scroll-view 生效 */
    height: calc(100vh - 100rpx - var(--status-bar-height)); 
}

.post-item {
  background: #fff; margin: 20rpx; padding: 30rpx; border-radius: 16rpx;
}
.post-header { display: flex; align-items: center; margin-bottom: 20rpx; }
.avatar { width: 60rpx; height: 60rpx; border-radius: 50%; margin-right: 20rpx; background: #eee;}
.nickname { font-weight: bold; font-size: 28rpx; flex: 1; }
.time { font-size: 24rpx; color: #999; }

.post-content { 
    font-size: 30rpx; 
    color: #333; 
    margin-bottom: 20rpx; 
    line-height: 1.5;
    /* 限制显示行数，比如最多显示3行 */
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    overflow: hidden;
}

.post-images { position: relative; width: 200rpx; height: 200rpx; margin-bottom: 20rpx; }
.post-img { width: 100%; height: 100%; border-radius: 8rpx; background: #f0f0f0; }
.img-count {
  position: absolute; right: 10rpx; bottom: 10rpx; background: rgba(0,0,0,0.5);
  color: #fff; font-size: 20rpx; padding: 4rpx 10rpx; border-radius: 8rpx;
}
.post-footer { display: flex; justify-content: space-between; font-size: 24rpx; color: #999; border-top: 1rpx solid #eee; padding-top: 20rpx;}
.empty-state { text-align: center; color: #999; padding-top: 200rpx; }
.loading-more, .no-more { text-align: center; padding: 20rpx; color: #999; font-size: 24rpx; }
.loading-state { text-align: center; padding-top: 50rpx; color: #666;}
</style>
