<template>
  <view class="post-card">
    <view v-if="post.isTop" class="top-badge">PINNED</view>

    <view class="post-header">
      <view class="user-info" @click="handleUserClick">
        <image class="user-avatar" :src="getFullImageUrl(post.userAvatar)" mode="aspectFill"></image>
        <view class="user-detail">
          <view class="user-name-row">
            <text class="user-name">{{ post.userName }}</text>
          </view>
          <text class="post-time">{{ post.time }}</text>
        </view>
      </view>
      <text class="post-more" @click="handleMoreClick">...</text>
    </view>

    <view class="post-content" @click="handlePostClick">
      <view class="post-tag" v-if="post.tag">
        <text>{{ post.tag }}</text>
      </view>
      <text v-if="post.title" class="post-title">{{ post.title }}</text>
      <text class="post-text">{{ post.content }}</text>
      <view v-if="post.tags && post.tags.length" class="post-tags">
        <text v-for="tag in post.tags" :key="tag" class="content-tag">#{{ tag }}</text>
      </view>
    </view>

    <view class="post-images" v-if="post.images && post.images.length > 0">
      <image
        class="image-item"
        v-for="(img, index) in post.images"
        :key="index"
        :src="getFullImageUrl(img)"
        mode="aspectFill"
        @click.stop="handleImageClick(index)"
      ></image>
    </view>

    <view class="product-info" v-if="post.product" @click="handleProductClick">
      <text class="product-tag">ON SALE</text>
      <text class="product-price">PRICE ${{ post.product.price }}</text>
    </view>

    <view class="post-footer">
      <view class="footer-item">
        <text class="icon">V</text>
        <text>{{ formatNumber(post.views) }}</text>
      </view>
      <view class="footer-item" @click="handleTopClick">
        <text class="icon">P</text>
        <text>{{ post.isTop ? 'Unpin' : 'Pin' }}</text>
      </view>
      <view class="footer-item" @click="handleLikeClick">
        <text class="icon">{{ isLiked ? '♥' : '♡' }}</text>
        <text>{{ formatNumber(post.likes) }}</text>
      </view>
      <view class="footer-item" @click="handleCommentClick">
        <text class="icon">C</text>
        <text>{{ formatNumber(post.comments) }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { toAbsoluteUrl } from '@/utils/api'

export default {
  name: 'PostCard',
  props: {
    post: {
      type: Object,
      required: true,
      default: () => ({})
    }
  },
  computed: {
    isLiked() {
      return !!(this.post && this.post.isLiked)
    }
  },
  methods: {
    getFullImageUrl(url) {
      return toAbsoluteUrl(url)
    },
    formatNumber(num) {
      if (!num) return '0'
      const n = parseInt(num)
      if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
      return String(num)
    },
    handleUserClick() { this.$emit('user-click', this.post) },
    handleMoreClick() { this.$emit('more-click', this.post) },
    handlePostClick() { this.$emit('post-click', this.post) },
    handleImageClick(index) { this.$emit('image-click', { post: this.post, imageIndex: index }) },
    handleProductClick() { this.$emit('product-click', this.post) },
    handleTopClick() { this.$emit('top-click', this.post) },
    handleCommentClick() { this.$emit('comment-click', this.post) },
    handleLikeClick() {
      this.$emit('like-click', { post: this.post, isLiked: !this.isLiked })
    }
  }
}
</script>

<style scoped>
.top-badge {
  position: absolute;
  top: 18rpx;
  right: 18rpx;
  background: repeating-linear-gradient(135deg, rgba(255,255,255,.16) 0, rgba(255,255,255,.16) 2rpx, transparent 2rpx, transparent 16rpx), #181818;
  color: #fff;
  padding: 8rpx 18rpx;
  font-size: 22rpx;
  border-radius: 999rpx;
  box-shadow: 0 10rpx 24rpx rgba(0,0,0,.18);
  z-index: 10;
}
.post-card {
  position: relative;
  background: linear-gradient(145deg, rgba(255,255,255,.98), rgba(247,247,245,.94)), repeating-linear-gradient(125deg, rgba(17,17,17,.025) 0, rgba(17,17,17,.025) 2rpx, transparent 2rpx, transparent 28rpx);
  border-radius: 28rpx;
  padding: 30rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid rgba(17,17,17,.08);
  box-shadow: 0 22rpx 60rpx rgba(17,17,17,.05);
}
.post-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20rpx; }
.user-info { display:flex; align-items:center; }
.user-avatar { width:80rpx; height:80rpx; border-radius:50%; background-color:#ececea; margin-right:20rpx; border:1rpx solid rgba(17,17,17,.08); }
.user-detail { display:flex; flex-direction:column; }
.user-name-row { display:flex; align-items:center; gap:10rpx; }
.user-name { font-size:28rpx; color:#191919; font-weight:bold; }
.post-time { font-size:24rpx; color:#8d8d87; margin-top:5rpx; }
.post-more { font-size:40rpx; color:#8d8d87; padding:10rpx; }
.post-content { margin-bottom:20rpx; }
.post-tag { display:inline-block; padding:6rpx 14rpx; background-color:rgba(17,17,17,.04); color:#444440; border-radius:999rpx; border:1rpx solid rgba(17,17,17,.08); font-size:22rpx; margin-right:10rpx; margin-bottom:10rpx; }
.post-text { display:block; font-size:28rpx; color:#262623; line-height:1.6; word-break:break-all; }
.post-title { display:block; margin-bottom:8rpx; font-size:31rpx; color:#131313; font-weight:bold; line-height:1.4; }
.post-tags { display:flex; flex-wrap:wrap; gap:10rpx; margin-top:14rpx; }
.content-tag { font-size:22rpx; color:#5f5f59; background-color:rgba(17,17,17,.04); padding:4rpx 12rpx; border-radius:999rpx; border:1rpx solid rgba(17,17,17,.06); }
.post-images { display:flex; gap:10rpx; margin-bottom:20rpx; flex-wrap:wrap; }
.image-item { width:200rpx; height:200rpx; border-radius:18rpx; background-color:#ececea; border:1rpx solid rgba(17,17,17,.08); }
.product-info { display:flex; align-items:center; gap:20rpx; padding:15rpx; background-color:rgba(17,17,17,.03); border-radius:16rpx; margin-bottom:20rpx; border:1rpx solid rgba(17,17,17,.08); }
.product-tag { font-size:24rpx; color:#5e5e59; }
.product-price { font-size:28rpx; color:#1b1b1b; font-weight:bold; }
.post-footer { display:flex; justify-content:space-around; padding-top:20rpx; border-top:1rpx solid rgba(17,17,17,.08); }
.footer-item { display:flex; align-items:center; gap:8rpx; font-size:24rpx; color:#676762; padding:10rpx 15rpx; }
.footer-item:active { background-color:rgba(17,17,17,.05); border-radius:14rpx; }
.footer-item .icon { font-size:28rpx; }
</style>
