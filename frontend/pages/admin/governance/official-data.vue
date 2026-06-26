<template>
  <view class="page">
    <view class="header">
      <view class="status-bar"></view>
      <view class="nav">
        <view class="nav-btn" @click="goBack">&lt;</view>
        <view class="nav-title">
          <text class="title">权威官方数据</text>
          <text class="subtitle">{{ pageSubtitle }}</text>
        </view>
        <view class="nav-btn small" @click="refreshList">刷新</view>
      </view>
    </view>

    <scroll-view scroll-y class="content" @scrolltolower="loadList">
      <view class="hero-card">
        <text class="hero-title">查看 aggregated_notice 中的权威官方数据</text>
        <text class="hero-text">
          这里直接读取系统聚合后的官方公告、通知与权威信息，不依赖社区治理事件表，因此清空监测事件后这里仍可正常查看。
        </text>

        <view class="toolbar">
          <input
            v-model.trim="keyword"
            class="search-input"
            placeholder="搜索标题、摘要或正文"
            confirm-type="search"
            @confirm="applyKeyword"
          />
          <button class="search-btn" @click="applyKeyword">搜索</button>
        </view>

        <view class="filter-row">
          <view class="filter-pill" @click="selectCategory">
            <text>分类</text>
            <text class="filter-value">{{ activeCategory || '全部分类' }}</text>
          </view>
        </view>

        <view class="hero-meta">
          <text>当前数据源：aggregated_notice</text>
        </view>
      </view>

      <view v-if="loading && records.length === 0" class="state-card">官方数据加载中...</view>
      <view v-else-if="records.length === 0" class="state-card">当前没有匹配条件的官方数据</view>

      <view v-else class="card-list">
        <view v-for="item in records" :key="item.id" class="data-card">
          <view class="card-top">
            <view class="badge-group">
              <text class="type-badge">{{ item.category || '未分类' }}</text>
              <text class="confidence-badge">{{ item.sourceName || '官方来源' }}</text>
            </view>
            <text class="time">{{ formatDate(item.publishTime) }}</text>
          </view>

          <text class="card-title">{{ item.title || '未命名官方数据' }}</text>
          <text class="card-text">{{ item.summary || '暂无摘要内容' }}</text>

          <view class="meta-list">
            <text class="meta-item">状态：{{ item.status || 'ONLINE' }}</text>
            <text class="meta-item">对象：{{ item.targetAudience || '公开通知' }}</text>
            <text class="meta-item">地点：{{ item.location || '未注明' }}</text>
          </view>

        </view>
      </view>

      <view v-if="loadingMore" class="load-more loading-more">加载更多官方数据...</view>
      <view v-else-if="hasMore" class="load-more" @click="loadList">加载更多</view>
      <view style="height: 40rpx;"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { governanceApi } from '@/api/governance'

const keyword = ref('')
const activeKeyword = ref('')
const activeCategory = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const records = ref([])
const page = ref(1)
const hasMore = ref(false)
const categoryOptions = ref([])

const pageSubtitle = computed(() => {
  return activeCategory.value ? `${activeCategory.value} 分类` : '社区治理全量官方数据'
})

onLoad(async (options) => {
  keyword.value = options?.keyword || ''
  activeCategory.value = options?.category || ''
  activeKeyword.value = keyword.value
  await loadCategoryOptions()
  await refreshList()
})

onPullDownRefresh(async () => {
  await refreshList()
  uni.stopPullDownRefresh()
})

const refreshList = async () => {
  page.value = 1
  hasMore.value = false
  records.value = []
  activeKeyword.value = keyword.value
  await loadList(true)
}

const mergeUniqueById = (currentList, nextList) => {
  const seen = new Set(currentList.map(item => item.id))
  const appended = []
  for (const item of nextList) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    appended.push(item)
  }
  return appended
}

const loadCategoryOptions = async () => {
  try {
    const res = await governanceApi.getOfficialDataCategories()
    categoryOptions.value = res.data?.list || []
  } catch (error) {
    categoryOptions.value = []
  }
}

const applyKeyword = async () => {
  await refreshList()
}

const selectCategory = () => {
  const options = ['全部分类', ...categoryOptions.value]
  uni.showActionSheet({
    itemList: options,
    success: async ({ tapIndex }) => {
      activeCategory.value = tapIndex === 0 ? '' : options[tapIndex]
      await refreshList()
    }
  })
}

const loadList = async (force = false) => {
  const shouldForce = force === true

  if (loading.value || loadingMore.value) return
  if (!shouldForce && !hasMore.value && page.value > 1) return

  const isAppending = !shouldForce && records.value.length > 0 && page.value > 1

  try {
    if (isAppending) {
      loadingMore.value = true
    } else {
      loading.value = true
    }

    const res = await governanceApi.getOfficialData({
      page: page.value,
      pageSize: 10,
      category: activeCategory.value || undefined,
      keyword: activeKeyword.value || undefined
    })
    const list = res.data?.list || []
    if (page.value === 1) {
      records.value = list
      hasMore.value = !!res.data?.hasMore
    } else {
      const appended = mergeUniqueById(records.value, list)
      records.value = [...records.value, ...appended]
      hasMore.value = !!res.data?.hasMore && appended.length > 0
    }
    if (hasMore.value) {
      page.value += 1
    }
  } catch (error) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const formatDate = (value) => {
  if (!value || value === 'null') return '暂无时间'
  try {
    const date = new Date(value)
    return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  } catch (error) {
    return value
  }
}

const goBack = () => {
  uni.navigateBack()
}
</script>

<style scoped>
.page { min-height: 100vh; background: linear-gradient(180deg, #f5f5f3 0%, #ececea 46%, #f3f3f1 100%); }
.header { position: fixed; top: 0; left: 0; right: 0; z-index: 10; background: repeating-linear-gradient(135deg, rgba(255,255,255,.1) 0, rgba(255,255,255,.1) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #171717 0%, #3f3f3b 100%); color: #fff; box-shadow: 0 18rpx 40rpx rgba(17,17,17,.2); }
.status-bar { height: var(--status-bar-height); }
.nav { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 24rpx 24rpx; }
.nav-btn { width: 80rpx; height: 64rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.12); font-size: 38rpx; }
.nav-btn.small { width: 108rpx; font-size: 24rpx; }
.nav-title { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.title { font-size: 34rpx; font-weight: 700; }
.subtitle { font-size: 22rpx; opacity: .74; }
.content { height: 100vh; box-sizing: border-box; padding: calc(var(--status-bar-height) + 124rpx) 24rpx 28rpx; }
.hero-card, .state-card, .data-card { border-radius: 28rpx; border: 1rpx solid rgba(17,17,17,.08); box-shadow: 0 18rpx 48rpx rgba(17,17,17,.06); }
.hero-card { padding: 28rpx; background: linear-gradient(145deg, rgba(255,255,255,.97), rgba(247,247,245,.93)), repeating-linear-gradient(125deg, rgba(17,17,17,.02) 0, rgba(17,17,17,.02) 2rpx, transparent 2rpx, transparent 24rpx); margin-bottom: 22rpx; }
.hero-title { display: block; font-size: 34rpx; color: #171717; font-weight: 700; line-height: 1.45; }
.hero-text { display: block; margin-top: 12rpx; font-size: 25rpx; line-height: 1.7; color: #666660; }
.toolbar { display: flex; gap: 16rpx; margin-top: 22rpx; }
.filter-row { margin-top: 16rpx; }
.filter-pill { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 18rpx 22rpx; border-radius: 22rpx; background: rgba(17,17,17,.03); border: 1rpx solid rgba(17,17,17,.06); color: #666660; font-size: 24rpx; }
.filter-value { color: #171717; font-size: 26rpx; font-weight: 600; }
.search-input { flex: 1; height: 78rpx; padding: 0 24rpx; border-radius: 999rpx; background: rgba(17,17,17,.03); border: 1rpx solid rgba(17,17,17,.08); font-size: 26rpx; color: #171717; }
.search-btn { width: 180rpx; height: 78rpx; line-height: 78rpx; margin: 0; border-radius: 999rpx; background: repeating-linear-gradient(135deg, rgba(255,255,255,.14) 0, rgba(255,255,255,.14) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #1c1c1b 0%, #454540 100%); color: #fff; font-size: 28rpx; box-shadow: 0 16rpx 32rpx rgba(17,17,17,.16); }
.search-btn::after { border: none; }
.hero-meta { margin-top: 18rpx; font-size: 23rpx; color: #8d8d87; }
.state-card { padding: 48rpx 28rpx; text-align: center; background: rgba(255,255,255,.82); color: #8d8d87; font-size: 28rpx; }
.card-list { display: flex; flex-direction: column; gap: 18rpx; }
.data-card { padding: 26rpx; background: linear-gradient(180deg, rgba(255,255,255,.96), rgba(247,247,245,.94)); }
.card-top { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; }
.badge-group, .event-badges { display: flex; flex-wrap: wrap; gap: 10rpx; }
.type-badge, .confidence-badge, .event-badge { padding: 8rpx 14rpx; border-radius: 999rpx; font-size: 22rpx; font-weight: 600; }
.type-badge { background: rgba(17,17,17,.08); color: #2f2f2c; }
.confidence-badge { background: rgba(17,17,17,.04); color: #555550; }
.time { font-size: 22rpx; color: #8d8d87; }
.card-title { display: block; margin-top: 18rpx; font-size: 32rpx; font-weight: 700; color: #171717; line-height: 1.45; }
.card-text { display: block; margin-top: 14rpx; font-size: 25rpx; line-height: 1.7; color: #666660; }
.meta-list { display: flex; flex-direction: column; gap: 8rpx; margin-top: 18rpx; }
.meta-item { font-size: 23rpx; color: #777771; }
.load-more { margin: 22rpx auto 0; width: 320rpx; text-align: center; padding: 20rpx 0; border-radius: 999rpx; background: rgba(17,17,17,.05); color: #2f2f2c; font-size: 24rpx; border: 1rpx solid rgba(17,17,17,.06); }
.loading-more { pointer-events: none; }
</style>
