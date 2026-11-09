<template>
 <view class="page-container">
     <!-- 自定义头部 (绿色) -->
     <view class="custom-header neutral-theme">
       <!-- Status栏占位 (手机时间Signals栏) -->
       <view class="status-bar"></view>
       <!-- 标题栏 -->
       <view class="nav-bar">
         <text class="page-title">Errands</text>
       </view>
     </view>
    
    
    <!-- 内容区域 (需要加 padding-top 避开头部) -->
        <scroll-view scroll-y class="content" @scrolltolower="loadMore">
      <view 
        v-for="item in list" 
        :key="item.id" 
        class="errand-card"
        @click="goToDetail(item.id)"
      >
        <view class="card-header">
          <view class="user-info">
            <view class="avatar">👤</view>
            <text class="name">{{ item.publisher.nickname }}</text>
          </view>
          <view class="bounty-wrapper">
                       <text v-if="item.currency === 1" class="coin-icon">💰</text>
                       <text v-else class="money-icon">¥</text>
                       <text class="bounty-num">{{ item.bounty }}</text>
                    </view>
        </view>
        
        <view class="card-body">
          <text class="desc">{{ item.content }}</text>
          <view class="route">
            <text class="tag pick">Pickup</text> {{ item.pickupAddr }}
          </view>
          <view class="route">
            <text class="tag send">Delivery</text> {{ item.deliveryAddr }}
          </view>
        </view>
        
        <view class="card-footer">
          <text class="time">Posted just now</text>
          <view class="status-tag" :class="'s-'+item.status">
            {{ formatStatus(item.status) }}
          </view>
        </view>
      </view>
      
      <!-- 空Status -->
      <view v-if="list.length === 0" class="empty">No errands yet</view>
    </scroll-view>

    <TabBar current-tab="circle" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TabBar from '@/components/TabBar.vue'
import { errandApi } from '@/api/errand'

const list = ref([])

const syncPageTitle = (title) => {
  uni.setNavigationBarTitle({
    title
  })
  if (typeof document !== 'undefined') {
    document.title = title
  }
}

onShow(() => {
  syncPageTitle('Errand')
  uni.hideTabBar()
  loadData()
})

const loadData = async () => {
  try {
    const res = await errandApi.getList()
    if (res.code === 200) {
      list.value = res.data.list
    }
  } catch (error) {
    console.error(error)
  }
}

const goToPublish = () => {
  uni.navigateTo({ url: '/pages/errand/publish' })
}

const goToDetail = (id) => {
  uni.navigateTo({ url: `/pages/errand/detail?id=${id}` })
}

const formatStatus = (status) => {
  const map = { 0: 'Open', 1: 'In progress', 2: 'Completed', 3: 'Cancelled' }
  return map[status]
}

const loadMore = () => {
  // 加载更多逻辑
}
</script>

<style scoped>
.page-container { height: 100vh; display: flex; flex-direction: column; background: #ececec; }
.nav-header { 
  background: #fff; 
  padding: 80rpx 30rpx 20rpx; 
  display: flex; 
  justify-content: center; /* 居中标题 */
  align-items: center; 
  z-index: 10; 
}
/* --- 自定义头部样式 --- */
.custom-header {
  width: 100%;
  position: fixed;
  top: 0;
  z-index: 100;
  padding-bottom: 20rpx;
}

.neutral-theme {
  /* 你的绿色渐变 */
  background: linear-gradient(135deg, #f2f2f2 0%, #dedede 100%);
  box-shadow: 0 8rpx 22rpx rgba(17, 17, 17, 0.08);
}

.status-bar {
  /* 自动获取系统Status栏高度 */
  height: var(--status-bar-height); 
  width: 100%;
}

.nav-bar {
  height: 88rpx; /* 标准导航栏高度 */
  display: flex;
  justify-content: center; /* 标题居中 */
  align-items: center;
  position: relative;
}

.page-title {
  font-size: 34rpx;
  font-weight: bold;
  color: #1f1f1f;
}

/* 内容区域下移，给头部留出空间 */
.content {
  flex: 1;
  padding: 20rpx;
  box-sizing: border-box;
  padding-bottom: 160rpx;
  margin-top: calc(var(--status-bar-height) + 108rpx); /* Status栏+导航栏高度+余量 */
}
.title { font-size: 40rpx; font-weight: 800; }
.plus-btn { background: #efefef; color: #4f4f4f; padding: 10rpx 24rpx; border-radius: 30rpx; font-size: 26rpx; font-weight: bold; }

.content { flex: 1; padding: 20rpx; box-sizing: border-box; padding-bottom: 160rpx; }

.errand-card {
  background: #fff;
  padding: 30rpx;
  border-radius: 22rpx;
  margin-bottom: 24rpx;
  border: 1rpx solid rgba(17,17,17,0.05);
  box-shadow: 0 10rpx 30rpx rgba(17,17,17,0.06);
}
.card-header { display: flex; justify-content: space-between; margin-bottom: 20rpx; }
.user-info { display: flex; align-items: center; }
.avatar { width: 50rpx; height: 50rpx; background: #eee; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-right: 16rpx; }
.name { font-size: 26rpx; color: #666; }
.bounty { color: #ff4d4f; font-weight: bold; font-size: 32rpx; }

.desc { font-size: 30rpx; font-weight: bold; margin-bottom: 20rpx; display: block; }
.route { display: flex; align-items: center; font-size: 26rpx; color: #666; margin-bottom: 12rpx; }
.tag { font-size: 20rpx; padding: 2rpx 8rpx; border-radius: 4rpx; margin-right: 12rpx; color: #fff; }
.tag.pick { background: #7a7a7a; }
.tag.send { background: #3f3f3f; }

.card-footer { display: flex; justify-content: space-between; margin-top: 20rpx; padding-top: 20rpx; border-top: 1rpx solid #f9f9f9; }
.time { font-size: 24rpx; color: #ccc; }
.status-tag { font-size: 24rpx; font-weight: bold; }
.s-0 { color: #faad14; } /* Open */
.s-1 { color: #5a5a5a; } /* In progress */
.s-2 { color: #3f3f3f; }
/* 新增货币样式 */
.bounty-wrapper { display: flex; align-items: center; }
.coin-icon { font-size: 30rpx; margin-right: 4rpx; }
.money-icon { font-size: 28rpx; color: #ff4d4f; font-weight: bold; margin-right: 2rpx; }
.bounty-num { font-size: 36rpx; color: #ff4d4f; font-weight: bold; }
</style>
