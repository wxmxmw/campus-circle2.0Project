<template>
  <view class="waterfall-flow">
    <!-- 左列 -->
    <view class="column">
      <post-card 
        v-for="post in leftColumn" 
        :key="post.id"
        :post="post"
        @click="$emit('itemClick', post)"
      />
    </view>
    
    <!-- 右列 -->
    <view class="column">
      <post-card 
        v-for="post in rightColumn" 
        :key="post.id"
        :post="post"
        @click="$emit('itemClick', post)"
      />
    </view>
  </view>
  
  <!-- 加载状态 -->
  <view v-if="loading" class="loading">
    <uni-loading type="circle" />
    <text class="loading-text">Loading...</text>
  </view>
  
  <!-- 没有更多 -->
  <view v-if="!hasMore && data.length > 0" class="no-more">
    No more items
  </view>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import PostCard from './PostCard.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  hasMore: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['loadMore', 'itemClick'])

const leftColumn = ref([])
const rightColumn = ref([])
const leftHeight = ref(0)
const rightHeight = ref(0)

// 监听数据变化，重新分配瀑布流
watch(() => props.data, (newData) => {
  distributeItems(newData)
}, { immediate: true })

// 分配项目到左右列
const distributeItems = (items) => {
  leftColumn.value = []
  rightColumn.value = []
  leftHeight.value = 0
  rightHeight.value = 0
  
  items.forEach(item => {
    // 计算图片高度（简化计算，实际应根据图片比例）
    const imageHeight = item.images?.[0] 
      ? calculateImageHeight(item.images[0]) 
      : 300
    
    // 添加到高度较小的一列
    if (leftHeight.value <= rightHeight.value) {
      leftColumn.value.push(item)
      leftHeight.value += imageHeight + 200 // 200 为卡片其他内容高度
    } else {
      rightColumn.value.push(item)
      rightHeight.value += imageHeight + 200
    }
  })
}

// 计算图片高度
const calculateImageHeight = (image) => {
  // 根据图片宽高比计算显示高度
  // 瀑布流宽度约为 340rpx (750rpx / 2 - padding)
  const width = 340
  const ratio = image.height / image.width
  return width * ratio
}

// 触底加载更多
onReachBottom(() => {
  if (props.hasMore && !props.loading) {
    emit('loadMore')
  }
})
</script>

<style lang="scss" scoped>
.waterfall-flow {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
}

.column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40rpx 0;
}

.loading-text {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #999999;
}

.no-more {
  text-align: center;
  padding: 40rpx 0;
  font-size: 24rpx;
  color: #999999;
}
</style>