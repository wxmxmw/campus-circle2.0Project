<template>
  <view class="page-container">
    <view class="profile-header">
      <view class="checkin-btn-wrapper" v-if="userStore.isLoggedIn">
        <button class="checkin-btn" :class="{ checked: isCheckedIn }" @click="handleCheckIn" :disabled="isCheckedIn">
          {{ isCheckedIn ? 'Checked in' : 'Check in +10' }}
        </button>
      </view>

      <view class="user-card" @click="handleUserCardClick">
        <image v-if="userStore.isLoggedIn && userStore.avatar" :src="userStore.avatar" class="avatar-img" mode="aspectFill" />
        <view v-else class="avatar-placeholder">Me</view>

        <view class="info">
          <template v-if="userStore.isLoggedIn">
            <text class="username">{{ userStore.nickname }}</text>
            <text class="school">{{ userStore.userInfo.school || 'School not verified' }}</text>
            <text class="bio">{{ userStore.userInfo.bio || 'No bio yet.' }}</text>
          </template>
          <template v-else>
            <text class="username">Guest</text>
            <text class="desc">Tap to sign in or sign up</text>
          </template>
        </view>
      </view>

      <view class="stats" v-if="userStore.isLoggedIn">
        <view class="stat-item">
          <text class="num">{{ userStore.userInfo.points || 0 }}</text>
          <text class="label">My points</text>
        </view>
        <view class="stat-item" @click="handleFollowers">
          <text class="num">{{ userStore.userInfo.stats?.followers || 0 }}</text>
          <text class="label">Followers</text>
        </view>
        <view class="stat-item" @click="goToFollowList">
          <text class="num">{{ userStore.userInfo.stats?.following || 0 }}</text>
          <text class="label">Following</text>
        </view>
      </view>
    </view>

    <view class="menu-list">
      <view class="menu-item" @click="handleMyPosts"><text>My posts</text><text class="arrow">></text></view>
      <view class="menu-item" @click="handleMyErrands"><text>My errands</text><text class="arrow">></text></view>
      <view class="menu-item" @click="handleMyCollections"><text>Saved posts</text><text class="arrow">></text></view>
      <view class="menu-item" @click="handleSettings"><text>Profile settings</text><text class="arrow">></text></view>
      <view v-if="userStore.isAdmin" class="menu-item admin-entry" @click="handleAdmin"><text>Community moderation</text><text class="arrow">></text></view>
      <view v-if="userStore.isLoggedIn" class="menu-item logout" @click="handleLogout"><text>Sign out</text></view>
    </view>

    <view v-if="userStore.isLoggedIn && myPosts.length > 0" class="recent-posts">
      <view class="section-title">Recent posts</view>
      <view v-for="post in myPosts" :key="post.id" class="mini-post" @click="handlePostClick(post.id)">
        <text class="post-content">{{ post.content }}</text>
        <view class="post-meta">
          <text class="post-date">Views {{ post.stats?.views || 0 }}</text>
          <text class="post-date" style="margin-left: 20rpx">Likes {{ post.stats?.likes || 0 }}</text>
        </view>
      </view>
    </view>

    <TabBar current-tab="profile" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TabBar from '@/components/TabBar.vue'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'

const userStore = useUserStore()
const myPosts = ref([])
const isCheckedIn = ref(false)

const syncPageTitle = (title) => {
  uni.setNavigationBarTitle({
    title
  })
  if (typeof document !== 'undefined') {
    document.title = title
  }
}

onShow(() => {
  syncPageTitle('Profile')
  uni.hideTabBar()
  if (userStore.isLoggedIn) refreshData()
})

const refreshData = async () => {
  try {
    const [userRes, checkInRes, postRes] = await Promise.all([
      userApi.getUserInfo(),
      userApi.getCheckInStatus(),
      userApi.getMyPosts({ page: 1, size: 5 })
    ])
    if (userRes.code === 200) userStore.updateUserInfo(userRes.data)
    if (checkInRes.code === 200) isCheckedIn.value = checkInRes.data.checkedIn
    if (postRes.code === 200) myPosts.value = postRes.data.list
  } catch (e) {
    console.error('刷新数据Failed', e)
  }
}

const handleCheckIn = async () => {
  if (isCheckedIn.value) return
  try {
    uni.showLoading({ title: 'Checking in...' })
    const res = await userApi.checkIn()
    if (res.code === 200) {
      uni.showToast({ title: 'Checked in successfully', icon: 'success' })
      isCheckedIn.value = true
      userStore.updateUserInfo({ points: res.data.totalPoints })
    }
  } catch (error) {
    uni.showToast({ title: error.message || 'Check-in failed', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

const handlePostClick = (postId) => { uni.navigateTo({ url: `/pages/post/detail?id=${postId}` }) }
const handleUserCardClick = () => { if (!userStore.isLoggedIn) uni.navigateTo({ url: '/pages/login/index' }); else uni.navigateTo({ url: '/pages/profile/edit' }) }
const handleSettings = () => { if (userStore.isLoggedIn) uni.navigateTo({ url: '/pages/profile/edit' }) }
const handleMyErrands = () => { if (!userStore.isLoggedIn) return uni.navigateTo({ url: '/pages/login/index' }); uni.navigateTo({ url: '/pages/profile/my-errands' }) }
const handleMyPosts = () => { if (!userStore.isLoggedIn) return uni.navigateTo({ url: '/pages/login/index' }); uni.navigateTo({ url: '/pages/profile/my-post' }) }
const handleMyCollections = () => { if (!userStore.isLoggedIn) return uni.navigateTo({ url: '/pages/login/index' }); uni.navigateTo({ url: '/pages/profile/my-collection' }) }
const handleFollowers = () => { if (userStore.isLoggedIn) uni.navigateTo({ url: '/pages/profile/follow-list?type=followers' }) }
const goToFollowList = () => { if (userStore.isLoggedIn) uni.navigateTo({ url: '/pages/profile/follow-list?type=following' }) }
const handleAdmin = () => { uni.navigateTo({ url: '/pages/admin/governance/index' }) }
const handleLogout = () => {
  uni.showModal({
    title: 'Notice',
    content: 'Are you sure you want to sign out?',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        myPosts.value = []
        isCheckedIn.value = false
        uni.showToast({ title: 'Signed out', icon: 'none' })
      }
    }
  })
}
</script>

<style scoped>
.page-container { min-height: 100vh; padding: 20rpx 20rpx 160rpx; }
.profile-header,.menu-list,.recent-posts { background: linear-gradient(145deg, rgba(255,255,255,.96), rgba(247,247,245,.94)), repeating-linear-gradient(125deg, rgba(17,17,17,.02) 0, rgba(17,17,17,.02) 2rpx, transparent 2rpx, transparent 24rpx); border:1rpx solid rgba(17,17,17,.08); border-radius:30rpx; box-shadow:0 20rpx 56rpx rgba(17,17,17,.05); }
.profile-header { padding:96rpx 36rpx 36rpx; margin-bottom:20rpx; position:relative; }
.user-card { display:flex; align-items:center; margin-bottom:42rpx; }
.avatar-img,.avatar-placeholder { width:140rpx; height:140rpx; border-radius:50%; margin-right:30rpx; border:1rpx solid rgba(17,17,17,.08); }
.avatar-img { box-shadow:0 18rpx 36rpx rgba(17,17,17,.1); }
.avatar-placeholder { font-size:60rpx; display:flex; align-items:center; justify-content:center; background:linear-gradient(145deg, #ececea, #fff); }
.info { display:flex; flex-direction:column; flex:1; }
.username { font-size:40rpx; font-weight:bold; margin-bottom:10rpx; color:#171717; }
.school { font-size:24rpx; color:#343431; background:rgba(17,17,17,.05); padding:6rpx 14rpx; border-radius:999rpx; align-self:flex-start; margin-bottom:10rpx; border:1rpx solid rgba(17,17,17,.08); }
.bio,.desc { font-size:26rpx; color:#8d8d87; }
.stats { display:flex; justify-content:space-around; padding-top:24rpx; border-top:1rpx solid rgba(17,17,17,.08); }
.stat-item { display:flex; flex-direction:column; align-items:center; }
.num { font-weight:bold; font-size:36rpx; margin-bottom:6rpx; color:#171717; }
.label { font-size:24rpx; color:#8d8d87; }
.menu-list { margin-bottom:20rpx; overflow:hidden; }
.menu-item { padding:32rpx 36rpx; border-bottom:1rpx solid rgba(17,17,17,.06); font-size:30rpx; display:flex; justify-content:space-between; align-items:center; color:#1d1d1b; }
.menu-item:last-child { border-bottom:none; }
.menu-item:active { background-color:rgba(17,17,17,.04); }
.arrow { color:#9a9a94; font-family:monospace; }
.logout { color:#6d3030; justify-content:center; font-weight:bold; }
.recent-posts { padding:30rpx 36rpx; }
.section-title { font-weight:bold; margin-bottom:20rpx; font-size:30rpx; color:#171717; }
.mini-post { padding:20rpx 0; border-bottom:1rpx solid rgba(17,17,17,.06); }
.mini-post:last-child { border-bottom:none; }
.post-content { font-size:28rpx; color:#242422; margin-bottom:10rpx; display:block; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.post-meta { display:flex; align-items:center; }
.post-date { font-size:22rpx; color:#8d8d87; }
.checkin-btn-wrapper { position:absolute; top:34rpx; right:36rpx; }
.checkin-btn { background:repeating-linear-gradient(135deg, rgba(255,255,255,.16) 0, rgba(255,255,255,.16) 2rpx, transparent 2rpx, transparent 16rpx), linear-gradient(135deg, #1c1c1b 0%, #444440 100%); color:#fff; font-size:24rpx; padding:0 30rpx; height:60rpx; line-height:60rpx; border-radius:999rpx; box-shadow:0 14rpx 28rpx rgba(17,17,17,.16); border:none; }
.checkin-btn.checked { background:rgba(17,17,17,.08); color:#8d8d87; box-shadow:none; }
.checkin-btn::after { border:none; }
.admin-entry { background-color:rgba(17,17,17,.03); }
.admin-entry text { color:#1f1f1d; font-weight:bold; }
</style>
