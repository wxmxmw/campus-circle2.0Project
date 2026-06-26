<template>
  <view class="home-page">
    <view class="header" :class="{ 'is-fixed': isNavFixed }">
      <view class="school-info">
        <view class="avatar">AI</view>
        <view class="school-name">
          <text class="name">Aether AI Campus</text>
          <text class="sub-name">Tongji Smart Community</text>
        </view>
      </view>
      <view class="header-actions">
        <text class="icon">◦</text>
        <text class="icon">⋯</text>
      </view>
    </view>

    <view class="header-placeholder" v-if="isNavFixed"></view>

    <view class="search-bar">
      <text class="search-icon">S</text>
      <input
        class="search-input"
        type="text"
        v-model="keyword"
        placeholder="Search posts, users, or keywords"
        @confirm="search"
      />
    </view>

    <HotTopics :topics="topics" @topic-click="handleTopicClick" @more-click="handleTopicsMore" />

    <CategoryNav
      :categories="categories"
      :current-category="currentCategory"
      :is-fixed="isNavFixed"
      @category-change="switchCategory"
    />

    <view class="post-list">
      <PostCard
        v-for="post in posts"
        :key="post.id"
        :post="post"
        @user-click="handleUserClick"
        @more-click="handlePostMore"
        @post-click="handlePostClick"
        @image-click="handleImageClick"
        @product-click="handleProductClick"
        @comment-click="handleCommentClick"
        @like-click="handleLikeClick"
        @top-click="handleTopClick"
      />
    </view>

    <view class="bottom-space"></view>

    <TabBar :current-tab="currentTab" @tab-change="switchTab" />
  </view>
</template>

<script>
import TabBar from '@/components/TabBar.vue';
import PostCard from '@/components/PostCard.vue';
import HotTopics from '@/components/HotTopics.vue';
import CategoryNav from '@/components/CategoryNav.vue';
import { userApi } from '@/api/user.js';
import { setPostTop, likePost } from '@/api/post.js';
import { toAbsoluteUrl } from '@/utils/api';
import { getHotTopics, getCategories, getPosts, searchPosts } from '@/api/index.js';

const normalizeHotPost = (post) => {
  const postId = Number(post?.id);
  return {
    ...post,
    id: Number.isFinite(postId) ? postId : null,
    title: typeof post?.title === 'string' ? post.title.trim() : '',
    viewsText: post?.viewsText || (post?.views ?? 0),
  };
};

export default {
  components: { TabBar, PostCard, HotTopics, CategoryNav },
  data() {
    return {
      currentTab: 'home',
      currentCategory: 0,
      isNavFixed: false,
      topics: [],
      categories: [],
      posts: [],
      keyword: '',
      page: 1,
      pageSize: 10,
      hasMore: true,
      loadingPosts: false,
    };
  },
  async onLoad() {
    this.syncPageTitle('Aether AI Campus');
    await Promise.all([this.loadTopics(), this.loadCategories(), this.loadPosts()]);
  },
  onShow() {
    this.syncPageTitle('Aether AI Campus');
    uni.hideTabBar({ animation: false });
    this.loadPosts(true);
  },
  onPageScroll(e) {
    this.isNavFixed = e.scrollTop > 400;
  },
  onReachBottom() {
    this.loadPosts(false);
  },
  onPullDownRefresh() {
    this.loadPosts(true).finally(() => {
      uni.stopPullDownRefresh();
    });
  },
  methods: {
    syncPageTitle(title) {
      uni.setNavigationBarTitle({
        title
      });
      if (typeof document !== 'undefined') {
        document.title = title;
      }
    },
    async loadTopics() {
      const res = await getHotTopics();
      if (res.code === 200) {
        this.topics = (Array.isArray(res.data) ? res.data : [])
          .map(normalizeHotPost)
          .filter(post => post.id);
      }
    },
    async handleTopClick(post) {
      if (post.isTop) {
        const res = await setPostTop(post.id, false);
        if (res.code === 200) {
          uni.showToast({ title: 'Unpinned', icon: 'none' });
          this.loadPosts(true);
        }
        return;
      }
      const userRes = await userApi.getUserInfo();
      const user = userRes.data;
      if ((user.points || 0) < 30) {
        return uni.showToast({ title: 'Need 30 points to pin', icon: 'none' });
      }
      const res = await setPostTop(post.id, true);
      if (res.code === 200) {
        uni.showToast({ title: 'Pinned', icon: 'success' });
        this.loadPosts(true);
      }
    },
    async loadCategories() {
      const res = await getCategories();
      if (res.code === 200) {
        const raw = Array.isArray(res.data?.list) ? res.data.list : [];
        const cats = raw.filter(c => c && (c.isActive === undefined || c.isActive));
        if (!cats.some(c => c && c.id === 0)) cats.unshift({ id: 0, name: 'All' });
        this.categories = cats;
      }
    },
    async loadPosts(reset = true) {
      if (this.loadingPosts) return;
      if (reset) {
        this.page = 1;
        this.posts = [];
        this.hasMore = true;
      }
      if (!reset && !this.hasMore) return;

      this.loadingPosts = true;
      const params = { page: this.page, pageSize: this.pageSize };
      const normalizedCategory = Number(this.currentCategory);
      if (Number.isFinite(normalizedCategory) && normalizedCategory > 0) {
        params.categoryId = normalizedCategory;
      }
      try {
        const res = await getPosts(params);
        if (res.code === 200) {
          const list = Array.isArray(res.data?.list) ? res.data.list : [];
          this.posts = reset ? list : [...this.posts, ...list];
          this.hasMore = !!res.data?.hasMore;
          if (this.hasMore) this.page++;
          this.posts.sort((a, b) => {
            const topDiff = (b.isTop ? 1 : 0) - (a.isTop ? 1 : 0);
            if (topDiff !== 0) return topDiff;
            return (new Date(b.time).getTime() || 0) - (new Date(a.time).getTime() || 0);
          });
        }
      } finally {
        this.loadingPosts = false;
      }
    },
    async switchCategory(id) {
      this.currentCategory = Number(id) || 0;
      await this.loadPosts(true);
    },
    async search() {
      const kw = (this.keyword || '').trim();
      if (!kw) return this.loadPosts(true);
      const res = await searchPosts(kw);
      if (res.code === 200) {
        this.posts = res.data?.list || [];
        this.hasMore = !!res.data?.hasMore;
        this.page = 1;
      }
    },
    handleTopicClick(post) {
      if (!post?.id) {
        uni.showToast({ title: '帖子数据异常', icon: 'none' });
        return;
      }
      uni.navigateTo({ url: `/pages/post/detail?id=${post.id}` });
    },
    handleTopicsMore() {
      uni.navigateTo({ url: '/pages/hot-posts/list' });
    },
    switchTab(tabId) {
      if (tabId === 'publish') uni.navigateTo({ url: '/pages/publish/index' });
      else this.currentTab = tabId;
    },
    handleUserClick(post) {
      const userId = post.userId || post.user?.id;
      if (userId) uni.navigateTo({ url: `/pages/user/home?id=${userId}` });
    },
    handlePostMore() {
      uni.showActionSheet({ itemList: ['Collect', 'Share', 'Report'] });
    },
    handlePostClick(post) {
      uni.navigateTo({ url: `/pages/post/detail?id=${post.id}` });
    },
    handleImageClick({ post, imageIndex }) {
      if (post.images && post.images.length > 0 && !post.images[0].startsWith('#')) {
        const urls = post.images.map(img => toAbsoluteUrl(img));
        uni.previewImage({ urls, current: imageIndex });
      }
    },
    handleProductClick(post) {
      uni.navigateTo({ url: `/pages/post/detail?id=${post.id}` });
    },
    handleCommentClick(post) {
      uni.navigateTo({ url: `/pages/post/detail?id=${post.id}&focus=comment` });
    },
    handleLikeClick({ post, isLiked }) {
      (async () => {
        try {
          const res = await likePost(post.id, isLiked);
          if (res.code === 200) {
            const target = this.posts.find(p => p.id === post.id);
            if (target) {
              target.isLiked = isLiked;
              const cur = parseInt(target.likes || 0) || 0;
              target.likes = Math.max(0, cur + (isLiked ? 1 : -1));
            }
          }
        } catch (e) {
          uni.showToast({ title: 'Like failed', icon: 'none' });
        }
      })();
    }
  }
};
</script>

<style scoped>
.home-page { min-height: 100vh; padding-bottom: 150rpx; }
.header { display:flex; justify-content:space-between; align-items:center; margin:18rpx 20rpx 0; padding:26rpx 28rpx; background:linear-gradient(145deg, rgba(255,255,255,.92), rgba(247,247,245,.88)), repeating-linear-gradient(120deg, rgba(17,17,17,.025) 0, rgba(17,17,17,.025) 2rpx, transparent 2rpx, transparent 26rpx); border:1rpx solid rgba(17,17,17,.08); border-radius:28rpx; box-shadow:0 20rpx 56rpx rgba(17,17,17,.06); transition:all .3s ease; z-index:100; }
.header.is-fixed { position:fixed; top:12rpx; left:20rpx; right:20rpx; backdrop-filter:blur(26rpx); -webkit-backdrop-filter:blur(26rpx); box-shadow:0 16rpx 42rpx rgba(17,17,17,.08); }
.header-placeholder { height:146rpx; }
.school-info { display:flex; align-items:center; }
.avatar { width:80rpx; height:80rpx; border-radius:50%; background:linear-gradient(145deg, #1b1b1a, #4b4b46); color:#fff; display:flex; align-items:center; justify-content:center; font-size:24rpx; font-weight:700; margin-right:20rpx; box-shadow:0 12rpx 24rpx rgba(17,17,17,.18); }
.name { font-size:32rpx; font-weight:bold; color:#171717; }
.sub-name { font-size:24rpx; color:#6d6d68; margin-top:4rpx; }
.header-actions { display:flex; gap:24rpx; }
.icon { font-size:38rpx; color:#444440; }
.search-bar { margin:22rpx 30rpx 0; padding:18rpx 26rpx; background:rgba(255,255,255,.78); border-radius:999rpx; display:flex; align-items:center; border:1rpx solid rgba(17,17,17,.08); box-shadow:0 14rpx 36rpx rgba(17,17,17,.04); backdrop-filter:blur(18rpx); -webkit-backdrop-filter:blur(18rpx); }
.search-icon { margin-right:15rpx; font-size:24rpx; color:#8d8d87; }
.search-input { flex:1; color:#777772; font-size:28rpx; }
.post-list { padding:20rpx 30rpx; }
.bottom-space { height:20rpx; }
</style>
