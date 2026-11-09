<template>
  <view class="detail-page" v-if="detail">
    <!-- Status横幅 -->
    <view class="status-banner" :class="'bg-'+detail.status">
      <text class="status-text">{{ formatStatus(detail.status) }}</text>
      <text class="status-desc">{{ getStatusDesc(detail.status) }}</text>
    </view>

    <view class="card info-card">
      <view class="header">
        <view class="user">
          <image
            v-if="detail.publisher?.avatar"
            :src="getFullImageUrl(detail.publisher.avatar)"
            class="avatar"
            mode="aspectFill"
          />
          <view v-else class="avatar">👤</view>
          <view>
            <!-- 修复：添加安全检查和fallback -->
            <view class="name">{{ detail.publisher?.nickname || detail.publisher?.username || 'Unknown user' }}</view>
            <view class="time">Posted 10 minutes ago</view>
          </view>
        </view>
        <view class="bounty">
          <template v-if="detail.currency === 1">💰 {{ detail.bounty }} points</template>
          <template v-else>¥ {{ detail.bounty }}</template>
        </view>
      </view>

      <view class="content-text">{{ detail.content }}</view>

      <view class="address-box">
        <view class="row">
          <text class="dot blue"></text>
          <text class="addr">{{ detail.pickupAddr }}</text>
        </view>
        <view class="line"></view>
        <view class="row">
          <text class="dot green"></text>
          <text class="addr">{{ detail.deliveryAddr }}</text>
        </view>
      </view>
    </view>

    <!-- 隐藏信息区域 -->
    <view class="card hidden-card">
      <view class="title">🔐 Private information</view>
      <view class="hidden-content">
        {{ detail.hiddenInfo }}
      </view>
      <text class="tip" v-if="detail.status === 0">Full details appear after accepting</text>
    </view>

    <!-- 底部操作栏 -->
    <view class="footer-action">
      <!-- 场景1: Me是Publish者，且订单Open -->
      <button
        v-if="isPublisher && detail.status === 0"
        class="btn cancel"
        @click="handleCancel"
      >Cancel errand</button>

      <!-- 场景2: Me是Publish者，且订单In progress -->
      <button
        v-if="isPublisher && detail.status === 1"
        class="btn confirm"
        @click="handleComplete"
      >Mark completed</button>

      <!-- 场景3: Me是路人，且订单Open -->
      <button
        v-if="!isPublisher && detail.status === 0"
        class="btn accept"
        @click="handleAccept"
      >Accept errand</button>

      <!-- 场景4: Errand closed -->
      <button v-if="detail.status >= 2" class="btn disabled" disabled>Errand closed</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { errandApi } from '@/api/errand'
import { useUserStore } from '@/stores/user'
import { getBackendOrigin } from '@/utils/api'

const userStore = useUserStore()
const detail = ref(null)
const errandId = ref('')

// 处理图片路径 - 参考Post详情页的实现
const BASE_URL = getBackendOrigin()

const getFullImageUrl = (url) => {
  if (!url) return '/static/logo.png'
  if (url.startsWith('http')) return url
  return BASE_URL + (url.startsWith('/') ? url : '/' + url)
}

// 判断是否是Publish者
const isPublisher = computed(() => {
  return detail.value && detail.value.publisher?.id === userStore.userInfo?.id
})

onLoad((options) => {
  errandId.value = options.id
  loadDetail()
})

const loadDetail = async () => {
  try {
    const res = await errandApi.getDetail(errandId.value)
    if (res.code === 200) {
      detail.value = res.data

      // 调试信息：输出关键数据
      console.log('=== 跑腿详情调试信息 ===')
      console.log('完整响应数据:', res.data)
      console.log('订单Status:', detail.value?.status)
      console.log('Publish者ID:', detail.value?.publisherId)
      console.log('当前用户ID:', userStore.userInfo?.id)
      console.log('Publish者信息:', detail.value?.publisher)
      console.log('是否为Publish者:', detail.value && detail.value.publisherId === userStore.userInfo?.id)

      // 详细检查publisherAudience
      if (detail.value?.publisher) {
        console.log('publisherAudience字段:')
        console.log('- id:', detail.value.publisher.id)
        console.log('- nickname:', detail.value.publisher.nickname)
        console.log('- username:', detail.value.publisher.username)
        console.log('- avatar:', detail.value.publisher.avatar)
      } else {
        console.warn('publisherAudience为空或未定义')
      }
    }
  } catch (error) {
    console.error('加载跑腿详情Failed:', error)
    uni.showToast({ title: 'Loading failed', icon: 'none' })
  }
}

// 抢单
const handleAccept = async () => {
  try {
    const res = await errandApi.accept(errandId.value)
    uni.showToast({ title: 'Errand accepted' })
    loadDetail() // 刷新获取隐藏信息
  } catch (error) {
    uni.showToast({ title: error.message, icon: 'none' })
  }
}

// Mark completed
const handleComplete = async () => {
  try {
    await errandApi.complete(errandId.value)
    uni.showToast({ title: 'Marked completed' })
    loadDetail()
  } catch (error) {
    uni.showToast({ title: 'Action failed', icon: 'none' })
  }
}

// Cancel errand
const handleCancel = async () => {
  try {
    await errandApi.cancel(errandId.value)
    uni.showToast({ title: 'Cancelled' })
    loadDetail()
  } catch (error) {
    uni.showToast({ title: 'Cancellation failed', icon: 'none' })
  }
}

const formatStatus = (s) => ['Open', 'In progress', 'Completed', 'Cancelled'][s]
const getStatusDesc = (s) => ['Waiting for someone to accept...', 'Delivery in progress', 'Errand completed', 'Errand cancelled'][s]
</script>

<style scoped>
.detail-page { min-height: 100vh; background: #F5F5F5; padding-bottom: 120rpx; }

.status-banner { padding: 40rpx; color: #fff; }
.bg-0 { background: linear-gradient(to right, #cfcfcf, #9c9c9c); }
.bg-1 { background: linear-gradient(to right, #d4d4d4, #8f8f8f); }
.bg-2 { background: linear-gradient(to right, #bdbdbd, #7a7a7a); }
.bg-3 { background: #d9d9d9; color: #666; }
.status-text { font-size: 40rpx; font-weight: bold; display: block; }
.status-desc { font-size: 26rpx; opacity: 0.9; }

.card { background: #fff; margin: 20rpx; border-radius: 16rpx; padding: 30rpx; }
.header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1rpx solid #eee; padding-bottom: 20rpx; margin-bottom: 20rpx; }
.user { display: flex; align-items: center; }
.avatar { width: 80rpx; height: 80rpx; background: #eee; border-radius: 50%; margin-right: 20rpx; display: flex; align-items: center; justify-content: center; }
.name { font-weight: bold; font-size: 30rpx; }
.time { font-size: 24rpx; color: #999; }
.bounty { font-size: 40rpx; color: #ff4d4f; font-weight: bold; }

.content-text { font-size: 32rpx; margin-bottom: 30rpx; line-height: 1.6; }

.address-box { background: #F9F9F9; padding: 20rpx; border-radius: 12rpx; }
.row { display: flex; align-items: center; margin: 10rpx 0; }
.dot { width: 16rpx; height: 16rpx; border-radius: 50%; margin-right: 16rpx; }
.dot.blue { background: #8a8a8a; }
.dot.green { background: #3f3f3f; }
.line { width: 2rpx; height: 30rpx; background: #ddd; margin-left: 7rpx; }
.addr { font-size: 28rpx; color: #333; }

.hidden-card .title { font-weight: bold; margin-bottom: 16rpx; }
.hidden-content { background: #f2f2f2; color: #525252; padding: 20rpx; border-radius: 8rpx; font-family: monospace; }
.tip { font-size: 24rpx; color: #ccc; margin-top: 10rpx; display: block; }

.footer-action { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; padding: 20rpx 30rpx; box-shadow: 0 -2rpx 10rpx rgba(0,0,0,0.05); }
.btn { border-radius: 40rpx; font-weight: bold; }
.btn.accept { background: #5a5a5a; color: #fff; }
.btn.confirm { background: #3f3f3f; color: #fff; }
.btn.cancel { background: #ff4d4f; color: #fff; }
.btn.disabled { background: #f5f5f5; color: #999; }
</style>
