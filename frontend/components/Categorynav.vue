<template>
  <view>
    <view class="category-nav-placeholder" v-if="isFixed"></view>
    <view class="category-nav-wrapper" :class="{ 'is-fixed': isFixed }">
      <scroll-view class="category-nav" scroll-x show-scrollbar="false">
        <view 
          v-for="(item, index) in categories" 
          :key="index"
          class="category-item"
          :class="{ active: currentCategory === item.id }"
          @click="handleCategoryClick(item.id)"
        >
          {{ item.name }}
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'CategoryNav',
  props: {
    categories: {
      type: Array,
      default: () => []
    },
    currentCategory: {
      type: [Number, String],
      default: 0
    },
    isFixed: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    handleCategoryClick(id) {
      this.$emit('category-change', id);
    }
  }
};
</script>

<style scoped>
.category-nav-placeholder {
  height: 268rpx;
}

.category-nav-wrapper {
  background-color: transparent;
  border-bottom: 1rpx solid rgba(17, 17, 17, 0.06);
  transition: all 0.3s ease;
  z-index: 99;
}

.category-nav-wrapper.is-fixed {
  position: fixed;
  top: 120rpx;
  left: 0;
  right: 0;
  background: rgba(243, 243, 241, 0.8);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  box-shadow: 0 12rpx 30rpx rgba(17, 17, 17, 0.05);
}

.category-nav {
  padding: 18rpx 30rpx 24rpx;
  white-space: nowrap;
}

.category-item {
  display: inline-block;
  padding: 12rpx 24rpx;
  margin-right: 16rpx;
  font-size: 28rpx;
  color: #6d6d68;
  border-radius: 999rpx;
  border: 1rpx solid rgba(17, 17, 17, 0.08);
  background: rgba(255, 255, 255, 0.55);
  transition: all 0.3s;
}

.category-item.active {
  background:
    repeating-linear-gradient(
      135deg,
      rgba(255, 255, 255, 0.12) 0,
      rgba(255, 255, 255, 0.12) 2rpx,
      transparent 2rpx,
      transparent 20rpx
    ),
    #171717;
  color: #ffffff;
  font-weight: bold;
  border-color: #171717;
  box-shadow: 0 10rpx 22rpx rgba(17, 17, 17, 0.14);
}
</style>
