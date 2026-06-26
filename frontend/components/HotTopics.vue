<template>
  <view class="hot-posts-card">
    <view class="card-header">
      <text class="card-title">热门帖子</text>
      <text class="more-btn" @click="handleMoreClick">查看更多</text>
    </view>

    <view class="posts-container">
      <view
        v-for="post in topics"
        :key="post.id"
        class="post-item"
        @click="handlePostClick(post)"
      >
        <view class="post-content">
          <text class="topic-tag">#</text>
          <text class="post-text">
            {{ displayTitle(post) }}
          </text>
          <text class="post-hot-icon">{{ post.hot ? 'HOT' : '' }}</text>
        </view>

        <view class="post-meta">
          <text class="post-views">浏览 {{ post.viewsText || post.views || 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'HotTopics',
  props: {
    topics: {
      type: Array,
      default: () => [],
    },
  },
  methods: {
    displayTitle(post) {
      const raw = (post && typeof post.title === 'string' ? post.title : '').trim()
      if (!raw) return '未命名帖子'
      return raw.length > 25 ? raw.slice(0, 25) + '...' : raw
    },
    handlePostClick(post) {
      const postId = Number(post?.id)
      if (!Number.isFinite(postId) || postId <= 0) {
        this.$emit('invalid-topic', post)
        return
      }
      this.$emit('topic-click', { ...post, id: postId })
    },
    handleMoreClick() {
      this.$emit('more-click')
    },
  },
}
</script>

<style scoped>
.hot-posts-card {
  margin: 20rpx 30rpx;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(247, 247, 245, 0.96)),
    repeating-linear-gradient(
      120deg,
      rgba(17, 17, 17, 0.02) 0,
      rgba(17, 17, 17, 0.02) 2rpx,
      transparent 2rpx,
      transparent 22rpx
    );
  border-radius: 28rpx;
  padding: 30rpx;
  border: 1rpx solid rgba(17, 17, 17, 0.08);
  box-shadow: 0 22rpx 60rpx rgba(17, 17, 17, 0.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25rpx;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid rgba(17, 17, 17, 0.08);
}

.card-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #171717;
}

.more-btn {
  font-size: 26rpx;
  color: #777772;
}

.topic-tag {
  color: #111111;
  font-size: 32rpx;
  font-weight: bold;
  margin-right: 10rpx;
}

.posts-container {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.post-item {
  padding: 20rpx;
  background-color: rgba(255, 255, 255, 0.72);
  border-radius: 20rpx;
  border: 1rpx solid rgba(17, 17, 17, 0.06);
  transition: all 0.2s;
}

.post-item:active {
  background-color: rgba(237, 237, 234, 0.95);
}

.post-content {
  display: flex;
  align-items: center;
  margin-bottom: 15rpx;
}

.post-text {
  font-size: 28rpx;
  color: #20201d;
  flex: 1;
}

.post-hot-icon {
  font-size: 22rpx;
  color: #8d8d87;
  margin-left: 10rpx;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 10rpx;
}

.post-views {
  font-size: 24rpx;
  color: #8d8d87;
}
</style>
