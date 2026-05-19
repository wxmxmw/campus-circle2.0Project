<template>
  <view class="publish-page">
    <view class="page-header">
      <text class="back-btn" @click="goBack">&lt;</text>
      <text class="page-title">Create</text>
      <text class="close-btn" @click="goBack">X</text>
    </view>

    <view class="hero">
      <text class="hero-title">What do you want to share?</text>
      <text class="hero-subtitle">Choose a publishing mode and keep moving.</text>
    </view>

    <view class="options-container">
      <view class="option-card" @click="handlePublish('post')">
        <text class="icon">P</text>
        <text class="opt-title">Post</text>
        <text class="opt-desc">Daily life / Marketplace / Talk</text>
      </view>

      <view class="option-card option-muted" @click="handlePublish('errand')">
        <text class="icon">E</text>
        <text class="opt-title">Errand</text>
        <text class="opt-desc">Pickup / Bounty / Mutual help</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  onLoad() {
    this.syncPageTitle('Create')
  },
  methods: {
    syncPageTitle(title) {
      uni.setNavigationBarTitle({
        title
      })
      if (typeof document !== 'undefined') {
        document.title = title
      }
    },
    goBack() {
      uni.navigateBack()
    },
    handlePublish(type) {
      if (type === 'post') {
        uni.navigateTo({
          url: '/pages/publish/Post'
        })
        return
      }

      if (type === 'errand') {
        uni.navigateTo({
          url: '/pages/errand/publish'
        })
      }
    }
  }
}
</script>

<style scoped>
.publish-page {
  min-height: 100vh;
  padding: 34rpx 30rpx 48rpx;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.96), rgba(245, 245, 245, 0.9) 42%, rgba(233, 233, 233, 0.92) 100%),
    linear-gradient(180deg, #fafafa 0%, #f1f1f1 54%, #ececec 100%);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
}

.back-btn,
.close-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 38rpx;
  color: #111111;
  background: rgba(255, 255, 255, 0.62);
  border: 1rpx solid rgba(17, 17, 17, 0.08);
  backdrop-filter: blur(18rpx);
}

.page-title {
  font-size: 28rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  color: #111111;
}

.hero {
  padding: 120rpx 2rpx 56rpx;
}

.hero-title {
  display: block;
  font-size: 56rpx;
  line-height: 1.08;
  font-weight: 600;
  color: #111111;
}

.hero-subtitle {
  display: block;
  margin-top: 18rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #707070;
}

.options-container {
  display: flex;
  flex-direction: column;
  gap: 26rpx;
}

.option-card {
  position: relative;
  padding: 38rpx 34rpx;
  border-radius: 28rpx;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.7);
  border: 1rpx solid rgba(17, 17, 17, 0.08);
  box-shadow: 0 20rpx 50rpx rgba(17, 17, 17, 0.06);
}

.option-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(120deg, rgba(17, 17, 17, 0.03) 0%, rgba(17, 17, 17, 0) 42%),
    repeating-linear-gradient(135deg, rgba(17, 17, 17, 0.035) 0 2rpx, transparent 2rpx 18rpx);
  opacity: 0.58;
  pointer-events: none;
}

.option-card:active {
  transform: scale(0.985);
}

.option-muted {
  background: rgba(248, 248, 248, 0.82);
}

.icon,
.opt-title,
.opt-desc {
  position: relative;
  z-index: 1;
}

.icon {
  display: block;
  font-size: 44rpx;
  line-height: 1;
  color: #111111;
}

.opt-title {
  display: block;
  margin-top: 26rpx;
  font-size: 36rpx;
  font-weight: 600;
  color: #111111;
}

.opt-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #666666;
}
</style>
