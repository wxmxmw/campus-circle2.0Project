<template>
  <view class="chat-page">
    <view class="nav-header">
      <text class="nav-btn" @click="goBack">&lt;</text>
      <view class="user-info">
        <text class="username">{{ chatUser.username || 'Chat' }}</text>
        <text v-if="chatUser.isOnline" class="online-status">Online</text>
      </view>
      <text class="nav-btn" @click="showChatMenu">...</text>
    </view>

    <scroll-view
      class="message-list"
      scroll-y
      :scroll-top="scrollTop"
      :scroll-with-animation="true"
      @scrolltoupper="loadMoreMessages"
    >
      <view v-if="hasMore" class="load-more" @click="loadMoreMessages">
        <text>{{ loading ? 'Loading...' : 'Load more messages' }}</text>
      </view>

      <view
        v-for="(msg, index) in messageList"
        :key="msg.id"
        class="message-item"
      >
        <view v-if="showTimeHeader(index)" class="time-divider">
          <text>{{ formatTime(msg.time) }}</text>
        </view>

        <view v-if="msg.senderId !== currentUserId" class="message-row other">
          <image
            class="avatar"
            :src="getFullImageUrl(chatUser.avatar)"
            mode="aspectFill"
          />
          <view class="message-bubble other">
            <text v-if="msg.type === 'text'" class="message-text">{{ msg.content }}</text>
            <image
              v-else
              class="message-image"
              :src="getFullImageUrl(msg.content)"
              @click="previewImage(getFullImageUrl(msg.content))"
            />
          </view>
        </view>

        <view v-else class="message-row self">
          <view class="message-bubble self">
            <text v-if="msg.status === 'sending'" class="status-icon">...</text>
            <text v-else-if="msg.status === 'failed'" class="status-icon failed" @click="resendMessage(msg)">!</text>

            <text v-if="msg.type === 'text'" class="message-text">{{ msg.content }}</text>
            <image
              v-else
              class="message-image"
              :src="getFullImageUrl(msg.content)"
              @click="previewImage(getFullImageUrl(msg.content))"
            />
          </view>

          <image
            class="avatar"
            :src="currentUserAvatar ? getFullImageUrl(currentUserAvatar) : '/static/logo.png'"
            mode="aspectFill"
          />
        </view>
      </view>

      <view class="list-bottom"></view>
    </scroll-view>

    <view class="input-bar">
      <view class="input-wrapper">
        <input
          class="message-input"
          v-model="inputText"
          placeholder="Send a message..."
          :adjust-position="true"
          confirm-type="send"
          @confirm="sendTextMessage"
          @focus="handleInputFocus"
        />
      </view>
      <text class="tool-btn" @click="toggleEmojiPanel">:)</text>
      <text class="tool-btn" @click="chooseImage">IMG</text>
      <view class="send-btn" :class="{ active: inputText.trim() }" @click="sendTextMessage">
        Send
      </view>
    </view>

    <view v-if="showEmoji" class="emoji-panel">
      <view class="emoji-grid">
        <text
          v-for="emoji in emojiList"
          :key="emoji"
          class="emoji-item"
          @click="insertEmoji(emoji)"
        >{{ emoji }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getChatMessages, sendMessage, getChatUserInfo } from '../../api/message.js'
import { toAbsoluteUrl } from '../../utils/api.js'
import { useUserStore } from '@/stores/user'

export default {
  data() {
    return {
      userId: null,
      chatUser: {},
      currentUserId: null,
      currentUserAvatar: '/static/logo.png',
      messageList: [],
      inputText: '',
      scrollTop: 0,
      loading: false,
      hasMore: true,
      page: 1,
      showEmoji: false,
      emojiList: [':)', ':D', ';)', '<3', 'T_T', ':P', '^^', 'OK', 'WOW', 'HI', 'GG', 'YO']
    }
  },

  onLoad(options) {
    this.syncPageTitle('Chat')
    const userStore = useUserStore()
    this.currentUserId = userStore.userInfo?.id

    if (userStore.userInfo) {
      this.currentUserAvatar = userStore.userInfo.avatar || userStore.userInfo.avatarUrl || '/static/logo.png'
    } else {
      userStore.$subscribe((mutation, state) => {
        if (state.userInfo) {
          this.currentUserAvatar = state.userInfo.avatar || state.userInfo.avatarUrl || '/static/logo.png'
        }
      })
    }

    if (options.userId) {
      this.userId = Number(options.userId)
      this.initChat()
    }
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

    async initChat() {
      await this.fetchUserInfo()
      await this.fetchMessages()
      this.scrollToBottom()
    },

    async fetchUserInfo() {
      try {
        const res = await getChatUserInfo(this.userId)
        if (res.code === 200) {
          this.chatUser = res.data || {}
          this.syncPageTitle(this.chatUser.username || 'Chat')
        }
      } catch (error) {
        console.error('Failed to fetch chat user info:', error)
      }
    },

    getFullImageUrl(url) {
      return toAbsoluteUrl(url, '/static/logo.png')
    },

    async fetchMessages() {
      if (this.loading) {
        return
      }

      this.loading = true
      try {
        const res = await getChatMessages(this.userId, this.page)
        if (res.code === 200) {
          const messages = (res.data.list || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time))
          if (this.page === 1) {
            this.messageList = messages
          } else {
            this.messageList = [...messages, ...this.messageList]
          }
          this.hasMore = Boolean(res.data.hasMore)
        }
      } catch (error) {
        console.error('Failed to fetch messages:', error)
      } finally {
        this.loading = false
      }
    },

    async loadMoreMessages() {
      if (this.loading || !this.hasMore) {
        return
      }
      this.page += 1
      await this.fetchMessages()
    },

    async sendTextMessage() {
      const content = this.inputText.trim()
      if (!content) {
        return
      }

      const tempMsg = {
        id: Date.now(),
        senderId: this.currentUserId,
        receiverId: this.userId,
        type: 'text',
        content,
        time: new Date().toISOString(),
        status: 'sending'
      }

      this.messageList.push(tempMsg)
      this.inputText = ''
      this.showEmoji = false
      this.scrollToBottom()

      try {
        const res = await sendMessage({
          receiverId: this.userId,
          type: 'text',
          content
        })

        const index = this.messageList.findIndex((item) => item.id === tempMsg.id)
        if (index === -1) {
          return
        }

        if (res.code === 200) {
          this.messageList[index].status = 'sent'
          this.messageList[index].id = res.data.id
        } else {
          this.messageList[index].status = 'failed'
        }
      } catch (error) {
        const index = this.messageList.findIndex((item) => item.id === tempMsg.id)
        if (index > -1) {
          this.messageList[index].status = 'failed'
        }
      }
    },

    async resendMessage(msg) {
      uni.showModal({
        title: 'Retry',
        content: 'Send this message again?',
        success: async (res) => {
          if (!res.confirm) {
            return
          }

          msg.status = 'sending'
          try {
            const result = await sendMessage({
              receiverId: this.userId,
              type: msg.type,
              content: msg.content
            })

            if (result.code === 200) {
              msg.status = 'sent'
              msg.id = result.data.id
            } else {
              msg.status = 'failed'
            }
          } catch (error) {
            msg.status = 'failed'
          }
        }
      })
    },

    chooseImage() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          const tempFilePath = res.tempFilePaths[0]
          const tempMsg = {
            id: Date.now(),
            senderId: this.currentUserId,
            receiverId: this.userId,
            type: 'image',
            content: tempFilePath,
            time: new Date().toISOString(),
            status: 'sending'
          }

          this.messageList.push(tempMsg)
          this.scrollToBottom()

          setTimeout(() => {
            const index = this.messageList.findIndex((item) => item.id === tempMsg.id)
            if (index > -1) {
              this.messageList[index].status = 'sent'
            }
          }, 1500)
        }
      })
    },

    previewImage(url) {
      const imageUrls = this.messageList
        .filter((item) => item.type === 'image')
        .map((item) => this.getFullImageUrl(item.content))

      uni.previewImage({
        current: url,
        urls: imageUrls
      })
    },

    insertEmoji(emoji) {
      this.inputText += emoji
    },

    toggleEmojiPanel() {
      this.showEmoji = !this.showEmoji
    },

    handleInputFocus() {
      this.showEmoji = false
      this.scrollToBottom()
    },

    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollTop = 999999
      })
    },

    showTimeHeader(index) {
      if (index === 0) {
        return true
      }

      const current = new Date(this.messageList[index].time)
      const previous = new Date(this.messageList[index - 1].time)
      return current - previous > 5 * 60 * 1000
    },

    formatTime(timeStr) {
      const date = new Date(timeStr)
      const now = new Date()
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
      const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`

      if (date >= today) {
        return time
      }
      if (date >= yesterday) {
        return `Yesterday ${time}`
      }
      return `${date.getMonth() + 1}/${date.getDate()} ${time}`
    },

    showChatMenu() {
      uni.showActionSheet({
        itemList: ['View profile', 'Clear chat', 'Report'],
        success: (res) => {
          if (res.tapIndex === 0) {
            this.viewProfile()
          } else if (res.tapIndex === 1) {
            this.clearMessages()
          } else if (res.tapIndex === 2) {
            this.reportUser()
          }
        }
      })
    },

    viewProfile() {
      uni.navigateTo({
        url: `/pages/user/home?id=${this.userId}`
      })
    },

    clearMessages() {
      uni.showModal({
        title: 'Clear chat',
        content: 'Delete all messages in this chat?',
        success: (res) => {
          if (res.confirm) {
            this.messageList = []
            uni.showToast({
              title: 'Chat cleared',
              icon: 'none'
            })
          }
        }
      })
    },

    reportUser() {
      uni.showActionSheet({
        itemList: ['Spam', 'Harassment', 'Fraud', 'Other'],
        success: () => {
          uni.showToast({
            title: 'Report sent',
            icon: 'none'
          })
        }
      })
    },

    goBack() {
      uni.navigateBack()
    }
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background:
    radial-gradient(circle at top, rgba(255, 255, 255, 0.92), rgba(243, 243, 243, 0.9) 40%, rgba(234, 234, 234, 0.95) 100%),
    linear-gradient(180deg, #f7f7f7 0%, #ededed 100%);
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 28rpx 18rpx;
  background: rgba(255, 255, 255, 0.72);
  border-bottom: 1rpx solid rgba(17, 17, 17, 0.08);
  backdrop-filter: blur(18rpx);
  flex-shrink: 0;
}

.nav-btn {
  width: 70rpx;
  height: 70rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 36rpx;
  color: #111111;
  background: rgba(255, 255, 255, 0.6);
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.username {
  font-size: 30rpx;
  font-weight: 600;
  color: #111111;
}

.online-status {
  margin-top: 4rpx;
  font-size: 20rpx;
  letter-spacing: 1rpx;
  color: #767676;
}

.message-list {
  flex: 1;
  padding: 24rpx 28rpx 0;
}

.load-more {
  text-align: center;
  padding: 18rpx 0 22rpx;
  font-size: 22rpx;
  color: #888888;
}

.message-item {
  margin-bottom: 22rpx;
}

.time-divider {
  text-align: center;
  padding: 10rpx 0 18rpx;
}

.time-divider text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(17, 17, 17, 0.06);
  color: #7a7a7a;
  font-size: 20rpx;
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
}

.message-row.self {
  justify-content: flex-end;
}

.avatar {
  width: 78rpx;
  height: 78rpx;
  border-radius: 50%;
  background: #efefef;
  border: 1rpx solid rgba(17, 17, 17, 0.05);
  flex-shrink: 0;
}

.message-bubble {
  max-width: 66%;
  padding: 20rpx 24rpx;
  border-radius: 26rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  box-shadow: 0 14rpx 34rpx rgba(17, 17, 17, 0.05);
}

.message-bubble.other {
  background: rgba(255, 255, 255, 0.88);
  border: 1rpx solid rgba(17, 17, 17, 0.06);
  border-bottom-left-radius: 10rpx;
}

.message-bubble.self {
  background: linear-gradient(135deg, rgba(30, 30, 30, 0.94), rgba(74, 74, 74, 0.92));
  border-bottom-right-radius: 10rpx;
}

.message-text {
  font-size: 28rpx;
  line-height: 1.6;
  color: #1b1b1b;
  word-break: break-word;
}

.message-bubble.self .message-text {
  color: #ffffff;
}

.message-image {
  width: 220rpx;
  min-height: 180rpx;
  border-radius: 20rpx;
  background: rgba(0, 0, 0, 0.04);
}

.status-icon {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.72);
}

.status-icon.failed {
  color: #ffb4b4;
}

.list-bottom {
  height: 22rpx;
}

.input-bar {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.82);
  border-top: 1rpx solid rgba(17, 17, 17, 0.06);
  backdrop-filter: blur(18rpx);
  flex-shrink: 0;
}

.input-wrapper {
  flex: 1;
  min-width: 0;
  padding: 0 26rpx;
  border-radius: 999rpx;
  background: rgba(17, 17, 17, 0.05);
}

.message-input {
  height: 74rpx;
  font-size: 28rpx;
  color: #111111;
}

.tool-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 34rpx;
  color: #111111;
  background: rgba(17, 17, 17, 0.05);
}

.send-btn {
  min-width: 120rpx;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 17, 17, 0.12);
  color: #6a6a6a;
  font-size: 26rpx;
  font-weight: 600;
}

.send-btn.active {
  background: #111111;
  color: #ffffff;
}

.emoji-panel {
  padding: 18rpx 20rpx calc(24rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.88);
  border-top: 1rpx solid rgba(17, 17, 17, 0.06);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12rpx;
}

.emoji-item {
  height: 72rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  background: rgba(17, 17, 17, 0.04);
}
</style>
