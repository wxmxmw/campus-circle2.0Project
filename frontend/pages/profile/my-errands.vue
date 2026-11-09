<template>
  <view class="container">
    <!-- 1. 新增：自定义顶部导航栏 (固定顶部) -->
    <view class="nav-header">
      <!-- BackBy钮区域，增大点击范围 -->
      <view class="back-btn-area" @click="goBack">
        <text class="back-arrow">←</text>
      </view>
      <text class="nav-title">My errands</text>
      <!-- 右侧占位，为了让标题绝对居中 -->
      <view class="right-placeholder"></view>
    </view>

    <!-- 2. 顶部 Tabs (位置下移，避开导航栏) -->
    <view class="tabs">
      <view 
        class="tab-item" 
        :class="{ active: currentType === 'published' }"
        @click="switchTab('published')"
      >
        Posted by me
      </view>
      <view 
        class="tab-item" 
        :class="{ active: currentType === 'accepted' }"
        @click="switchTab('accepted')"
      >
        Accepted by me
      </view>
    </view>

    <!-- 3. 列表内容 -->
    <scroll-view scroll-y class="list-container">
      <view 
        v-for="item in list" 
        :key="item.id" 
        class="errand-item"
        @click="goToDetail(item.id)"
      >
        <view class="item-header">
          <text class="time">{{ formatDate(item.createTime) }}</text>
          <text class="status" :class="'s-'+item.status">{{ formatStatus(item.status) }}</text>
        </view>
        
        <view class="item-body">
          <view class="content">{{ item.content }}</view>
          <view class="route-line">
            <text class="dot pick">Pickup</text> {{ item.pickupAddr }}
            <text class="arrow">→</text>
            <text class="dot send">Delivery</text> {{ item.deliveryAddr }}
          </view>
        </view>
        
        <view class="item-footer">
          <view class="bounty">
            <template v-if="item.currency === 1">💰 {{ item.bounty }}</template>
            <template v-else>¥ {{ item.bounty }}</template>
          </view>
          <view class="btn check-btn">View details</view>
        </view>
      </view>
      
      <view v-if="list.length === 0" class="empty">No matching errands</view>
    </scroll-view>
  </view>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'

const currentType = ref('published') // published | accepted
const list = ref([])

onMounted(() => {
  loadData()
})

const switchTab = (type) => {
  if (currentType.value === type) return
  currentType.value = type
  list.value = [] // 切换时先清空
  loadData()
}
// 新增：Back上一页
const goBack = () => {
  uni.navigateBack()
}

const loadData = async () => {
  try {
    uni.showLoading({ title: 'Loading' })
    const res = await userApi.getMyErrands(currentType.value)
    if (res.code === 200) {
      list.value = res.data.list
    }
  } finally {
    uni.hideLoading()
  }
}

const goToDetail = (id) => {
  uni.navigateTo({ url: `/pages/errand/detail?id=${id}` })
}

const formatStatus = (s) => ['Open', 'In progress', 'Completed', 'Cancelled'][s] || 'Unknown'
const formatDate = (ts) => {
  const d = new Date(ts)
  return `${d.getMonth()+1}-${d.getDate()} ${d.getHours()}:${d.getMinutes()}`
}
</script>

<style scoped>
/* 变量定义：方便统一修改 */
.container {
  min-height: 100vh;
  background: #f5f5f5;
  /* 关键点：整个页面内容避开 导航栏(88rpx) + Status栏高度 + Tabs高度(80rpx) */
  padding-top: calc(var(--status-bar-height) + 88rpx + 80rpx);
}

/* --- 1. 自定义导航栏样式 --- */
.nav-header {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 999;
  background-color: #fff;
  padding-top: var(--status-bar-height); /* 避开手机Status栏/刘海 */
  height: 88rpx; /* 标准导航栏内容高度 */
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.05);
}

.back-btn-area {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-arrow {
  font-size: 40rpx;
  color: #333;
  font-weight: bold;
}

.nav-title {
  font-size: 34rpx;
  font-weight: bold;
  color: #333;
}

.right-placeholder {
  width: 88rpx; /* 保持左右平衡，让标题居中 */
}

/* --- 2. Tabs 样式 (位置需要紧贴导航栏下方) --- */
.tabs {
  position: fixed;
  /* Tabs 的 top = Status栏高度 + 导航栏高度(88rpx) */
  top: calc(var(--status-bar-height) + 88rpx);
  left: 0;
  width: 100%;
  height: 80rpx;
  background: #fff;
  display: flex;
  z-index: 998;
  border-top: 1rpx solid #f0f0f0;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #666;
  position: relative;
}

.tab-item.active {
  color: #333333;
  font-weight: bold;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  width: 40rpx;
  height: 4rpx;
  background: #4a4a4a;
  border-radius: 2rpx;
}

/* --- 3. 列表样式 --- */
.list-container {
  padding: 20rpx;
  box-sizing: border-box;
  /* scroll-view 的高度 = 屏幕高度 - 头部占用高度 */
  height: calc(100vh - var(--status-bar-height) - 88rpx - 80rpx);
}

.errand-item {
  background: #fff;
  padding: 30rpx;
  border-radius: 22rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid rgba(17, 17, 17, 0.05);
  box-shadow: 0 10rpx 26rpx rgba(17,17,17,0.06);
}

.item-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20rpx;
  font-size: 24rpx;
}

.time { color: #999; }
.status { font-weight: bold; }
.s-0 { color: #666666; } /* Open */
.s-1 { color: #5a5a5a; } /* In progress */
.s-2 { color: #3f3f3f; }
.s-3 { color: #999; }    /* Cancelled */

.item-body { margin-bottom: 20rpx; }
.content { font-size: 30rpx; font-weight: bold; color: #333; margin-bottom: 16rpx; }
.route-line { font-size: 26rpx; color: #666; display: flex; align-items: center; }
.dot { padding: 2rpx 8rpx; border-radius: 4rpx; font-size: 20rpx; color: #fff; margin-right: 10rpx; }
.pick { background: #7a7a7a; }
.send { background: #4a4a4a; margin-left: 10rpx; }
.arrow { margin: 0 10rpx; color: #ccc; }

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1rpx solid #f5f5f5;
  padding-top: 20rpx;
}

.bounty { color: #ff4d4f; font-weight: bold; font-size: 32rpx; }
.check-btn {
  font-size: 24rpx;
  padding: 10rpx 30rpx;
  border: 1rpx solid #ddd;
  border-radius: 30rpx;
  color: #666;
}

.empty { text-align: center; color: #999; margin-top: 100rpx; font-size: 28rpx; }
</style>
