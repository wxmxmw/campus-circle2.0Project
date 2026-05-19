<template>
  <view class="message-page">
    <view class="header">
      <text class="title">Messages</text>
      <text class="subtitle">Chat and activity in one place.</text>
    </view>

    <view class="tab-bar">
      <view
        class="tab-item"
        :class="{ active: currentTab === 'chat' }"
        @click="switchTab('chat')"
      >
        <text>Chat</text>
        <view v-if="unreadChat > 0" class="badge">{{ unreadChat }}</view>
      </view>
      <view
        class="tab-item"
        :class="{ active: currentTab === 'notification' }"
        @click="switchTab('notification')"
      >
        <text>Activity</text>
        <view v-if="unreadNotify > 0" class="badge">{{ unreadNotify }}</view>
      </view>
    </view>

    <view v-if="currentTab === 'chat'" class="message-list">
      <view
        v-for="item in chatList"
        :key="item.id"
        class="message-card"
        @click="goToChat(item)"
      >
        <image class="avatar" :src="item.avatar" mode="aspectFill"></image>
        <view class="message-info">
          <text class="username">{{ item.username }}</text>
          <text class="content">{{ item.lastMessage }}</text>
        </view>
        <view class="message-right">
          <text class="time">{{ item.time }}</text>
          <view v-if="item.unread > 0" class="unread-dot">{{ item.unread }}</view>
        </view>
      </view>

      <view v-if="chatList.length === 0" class="empty-tip">
        No private messages yet.
      </view>
    </view>

    <view v-if="currentTab === 'notification'" class="message-list">
      <view
        v-for="item in notifyList"
        :key="item.id"
        class="notify-card"
        :class="{ unread: !item.isRead }"
        @click="goToPostDetail(item)"
      >
        <view v-if="!item.isRead" class="notify-unread-dot"></view>

        <image class="avatar" :src="item.avatar" mode="aspectFill"></image>
        <view class="notify-info">
          <view class="info-header">
            <text class="username">{{ item.username }}</text>
            <text class="notify-type">{{ item.typeText }} {{ item.time }}</text>
          </view>

          <text
            v-if="(item.type === 'comment' || item.type === 'reply') && item.commentContent"
            class="comment-text"
          >
            {{ item.commentContent }}
          </text>

          <view v-if="item.quote" class="quote-content">
            <text class="quote-label">{{ item.quoteLabel || 'Quote' }}:</text>
            <text>{{ item.quote }}</text>
          </view>

          <view
            v-if="item.type === 'comment' || item.type === 'reply'"
            class="reply-btn"
            @click.stop="handleReply(item)"
          >
            Reply
          </view>
        </view>

        <text class="arrow-icon">&gt;</text>
      </view>

      <view v-if="notifyList.length === 0" class="empty-tip">
        No activity yet.
      </view>
    </view>

    <view class="bottom-space"></view>

    <TabBar :current-tab="'message'" @tab-change="handleTabChange" />
  </view>
</template>

<script>
import TabBar from '@/components/TabBar.vue'
import { getChatList, getNotifyList, markChatAsRead, markNotifyAsRead } from '../../api/message.js'

export default {
  components: {
    TabBar
  },
  data() {
    return {
      currentTab: 'chat',
      chatList: [],
      notifyList: [],
      unreadChat: 0,
      unreadNotify: 0
    }
  },

  onLoad() {
    this.syncPageTitle('Messages')
    this.initData()
  },

  onShow() {
    this.syncPageTitle('Messages')
    this.fetchChatList()
    this.fetchNotifyList()
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

    async initData() {
      await Promise.all([this.fetchChatList(), this.fetchNotifyList()])
    },

    async fetchChatList() {
      try {
        const res = await getChatList()
        if (res.code === 200) {
          const uniqueChats = new Map()
          ;(res.data.list || []).forEach((chat) => {
            if (!uniqueChats.has(chat.userId)) {
              uniqueChats.set(chat.userId, chat)
            }
          })
          this.chatList = Array.from(uniqueChats.values())
          this.unreadChat = res.data.unreadCount || 0
        }
      } catch (error) {
        console.error('Failed to fetch chat list:', error)
      }
    },

    async fetchNotifyList() {
      try {
        const res = await getNotifyList()
        if (res.code === 200) {
          this.notifyList = res.data.list || []
          this.unreadNotify = res.data.unreadCount || 0
        }
      } catch (error) {
        console.error('Failed to fetch notifications:', error)
      }
    },

    updateUnreadCounts() {
      this.unreadChat = this.chatList.reduce((sum, item) => sum + (item.unread || 0), 0)
      this.unreadNotify = this.notifyList.filter((item) => !item.isRead).length
    },

    switchTab(tab) {
      this.currentTab = tab
    },

    async goToChat(item) {
      if (item.unread > 0) {
        item.unread = 0
        this.updateUnreadCounts()
        try {
          await markChatAsRead(item.id)
        } catch (error) {
          console.error('Failed to mark chat as read:', error)
        }
      }

      uni.navigateTo({
        url: `/pages/chat/detail?userId=${item.userId}`
      })
    },

    async goToPostDetail(item) {
      if (!item.isRead) {
        item.isRead = true
        this.updateUnreadCounts()
        try {
          await markNotifyAsRead(item.id)
        } catch (error) {
          console.error('Failed to mark notification as read:', error)
        }
      }

      if (item.postId) {
        uni.navigateTo({
          url: `/pages/post/detail?id=${item.postId}`
        })
      }
    },

    async handleReply(item) {
      if (!item.isRead) {
        item.isRead = true
        this.updateUnreadCounts()
        try {
          await markNotifyAsRead(item.id)
        } catch (error) {
          console.error('Failed to mark notification as read:', error)
        }
      }

      if (!item.postId) {
        return
      }

      const params = {
        id: item.postId
      }

      if (item.commentId) {
        params.replyCommentId = item.commentId
        params.replyUsername = item.username
      }

      const queryString = Object.keys(params)
        .map((key) => `${key}=${encodeURIComponent(params[key])}`)
        .join('&')

      uni.navigateTo({
        url: `/pages/post/detail?${queryString}`
      })
    },

    handleTabChange(tabId) {
      if (tabId !== 'message') {
        console.log('Switch tab:', tabId)
      }
    }
  }
}
</script>

<style scoped>
.message-page {
  min-height: 100vh;
  padding-bottom: 150rpx;
  background:
    radial-gradient(circle at top, rgba(255, 255, 255, 0.96), rgba(243, 243, 243, 0.92) 44%, rgba(234, 234, 234, 0.94) 100%),
    linear-gradient(180deg, #f8f8f8 0%, #eeeeee 100%);
}

.header {
  padding: 34rpx 30rpx 28rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(245, 245, 245, 0.72));
  border-bottom: 1rpx solid rgba(17, 17, 17, 0.06);
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: #111111;
}

.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #7a7a7a;
}

.tab-bar {
  display: flex;
  gap: 44rpx;
  padding: 18rpx 30rpx 8rpx;
}

.tab-item {
  position: relative;
  padding: 10rpx 0 14rpx;
  font-size: 28rpx;
  color: #777777;
}

.tab-item.active {
  color: #111111;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 44rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #111111;
}

.badge {
  position: absolute;
  top: -8rpx;
  right: -28rpx;
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #111111;
  color: #ffffff;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-list {
  padding: 18rpx 24rpx;
}

.message-card,
.notify-card {
  display: flex;
  align-items: center;
  padding: 26rpx;
  margin-bottom: 18rpx;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.84);
  border: 1rpx solid rgba(17, 17, 17, 0.06);
  box-shadow: 0 12rpx 30rpx rgba(17, 17, 17, 0.05);
}

.notify-card {
  position: relative;
  align-items: flex-start;
  padding-left: 36rpx;
}

.notify-card.unread {
  background: rgba(255, 255, 255, 0.94);
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  margin-right: 20rpx;
  border-radius: 50%;
  background: #ededed;
  flex-shrink: 0;
}

.message-info,
.notify-info {
  flex: 1;
  min-width: 0;
}

.username {
  font-size: 28rpx;
  font-weight: 600;
  color: #111111;
}

.content,
.notify-type,
.quote-label {
  color: #7b7b7b;
}

.content {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  min-height: 96rpx;
  margin-left: 14rpx;
}

.time {
  font-size: 22rpx;
  color: #8f8f8f;
}

.unread-dot,
.notify-unread-dot {
  background: #111111;
  color: #ffffff;
}

.unread-dot {
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.notify-unread-dot {
  position: absolute;
  left: 14rpx;
  top: 36rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.notify-type {
  font-size: 22rpx;
}

.comment-text {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #222222;
}

.quote-content {
  margin-top: 8rpx;
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  background: rgba(17, 17, 17, 0.04);
  font-size: 24rpx;
  line-height: 1.6;
  color: #666666;
}

.reply-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 14rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: #111111;
  color: #ffffff;
  font-size: 22rpx;
}

.arrow-icon {
  margin-left: 10rpx;
  font-size: 28rpx;
  color: #9a9a9a;
  align-self: center;
}

.empty-tip {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #8a8a8a;
}

.bottom-space {
  height: 18rpx;
}
</style>
