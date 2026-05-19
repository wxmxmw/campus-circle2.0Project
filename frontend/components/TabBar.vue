<template>
  <view class="tabbar-container">
    <view class="tabbar">
      <view
        class="tabbar-item"
        v-for="(item, index) in tabbarList"
        :key="index"
        :class="{ active: currentTab === item.id, 'publish-btn': item.id === 'publish' }"
        @click.stop="handleTabClick(item.id)"
      >
        <view v-if="item.id === 'publish'" class="publish-circle">
          <text class="tabbar-icon">{{ item.icon }}</text>
        </view>
        <template v-else>
          <text class="tabbar-icon">{{ item.icon }}</text>
          <text class="tabbar-label">{{ item.label }}</text>
        </template>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'TabBar',
  props: {
    currentTab: {
      type: String,
      default: 'home'
    }
  },
  data() {
    return {
      tabbarList: [
        { id: 'home', label: 'Home', icon: '⌂', path: '/pages/index/index' },
        { id: 'circle', label: 'Errand', icon: '◎', path: '/pages/errand/index' },
        { id: 'publish', label: '', icon: '+', path: '/pages/publish/index' },
        { id: 'message', label: 'Message', icon: '◌', path: '/pages/message/index' },
        { id: 'profile', label: 'Me', icon: '◐', path: '/pages/profile/index' }
      ]
    };
  },
  methods: {
    handleTabClick(tabId) {
      if (tabId === this.currentTab) return;
      const target = this.tabbarList.find(item => item.id === tabId);
      if (tabId === 'publish') {
        uni.navigateTo({ url: target.path });
      } else {
        uni.switchTab({
          url: target.path,
          success: () => this.$emit('tab-change', tabId),
          fail: (err) => console.error('Switch Tab Failed:', err)
        });
      }
    }
  }
};
</script>

<style scoped>
.tabbar-container { position:fixed; bottom:0; left:0; right:0; padding:24rpx 28rpx; padding-bottom:calc(24rpx + env(safe-area-inset-bottom)); z-index:50; pointer-events:none; }
.tabbar { background:rgba(255,255,255,.82); border:1rpx solid rgba(17,17,17,.08); border-radius:40rpx; height:116rpx; display:flex; justify-content:space-around; align-items:center; backdrop-filter:blur(24rpx); -webkit-backdrop-filter:blur(24rpx); box-shadow:0 16rpx 48rpx rgba(17,17,17,.08); pointer-events:auto; }
.tabbar-item { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; position:relative; }
.tabbar-item.active .tabbar-icon,.tabbar-item.active .tabbar-label { color:#111111; }
.tabbar-icon { font-size:44rpx; color:#8d8d87; margin-bottom:4rpx; }
.tabbar-label { font-size:20rpx; color:#8d8d87; }
.publish-btn { position:relative; top:-24rpx; }
.publish-circle { width:110rpx; height:110rpx; border-radius:50%; background:linear-gradient(135deg, rgba(255,255,255,.35), rgba(255,255,255,0)), repeating-linear-gradient(135deg, rgba(255,255,255,.16) 0, rgba(255,255,255,.16) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #1b1b1a 0%, #3f3f3b 100%); display:flex; align-items:center; justify-content:center; box-shadow:0 18rpx 36rpx rgba(17,17,17,.24); border:1rpx solid rgba(255,255,255,.16); }
.publish-circle .tabbar-icon { color:#fff; font-size:50rpx; margin-bottom:0; }
</style>
