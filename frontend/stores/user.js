// stores/user.js - 用户状态管理 (Pinia)

import { defineStore } from 'pinia'

import { setToken, removeToken, getToken } from '@/utils/request'

import { ref, computed } from 'vue'
import { toAbsoluteUrl } from '@/utils/api'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(uni.getStorageSync('token') || '')
  const userInfo = ref(uni.getStorageSync('userInfo') || null)
  
  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const avatar = computed(() => toAbsoluteUrl(userInfo.value?.avatarUrl, '/static/logo.png'))
  const nickname = computed(() => userInfo.value?.nickname || 'Guest')
  
  // [新增] 管理员判断逻辑
    const isAdmin = computed(() => {
      // 使用可选链 ?. 防止 userInfo 为 null 时报错
      // 假设后端返回的管理员标识是 'ADMIN'
      return userInfo.value?.role === 'ADMIN'
    })

  // 动作：Signed in处理
  const setLoginState = (loginData) => {
    token.value = loginData.token
    userInfo.value = loginData.user
    
    // 持久化存储
    uni.setStorageSync('token', loginData.token)
    uni.setStorageSync('userInfo', loginData.user)
  }

  // 动作：更新用户信息 (U03)
  const updateUserInfo = (newInfo) => {
    userInfo.value = { ...userInfo.value, ...newInfo }
    uni.setStorageSync('userInfo', userInfo.value)
  }

  // 动作：Sign out (U05)
  const logout = () => {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    
    // 如果你在做 API 调用，这里应该调用 /auth/logout
    // userApi.logout() 
  }

  return {
    token,
    userInfo,
    isLoggedIn,
	isAdmin,
    avatar,
    nickname,
    setLoginState,
    updateUserInfo,
    logout
  }
})


// stores/chat.js - 聊天状态管理
export const useChatStore = defineStore('chat', {
  state: () => ({
    socket: null,
    isConnected: false,
    conversationList: [],
    currentConversation: null,
    messages: {},
    unreadCount: 0
  }),
  
  getters: {
    // 获取当前会话消息
    currentMessages: (state) => {
      if (!state.currentConversation) return []
      return state.messages[state.currentConversation.id] || []
    },
    
    // 获取未读消息数
    totalUnread: (state) => {
      return state.conversationList.reduce((total, conv) => total + (conv.unreadCount || 0), 0)
    }
  },
  
  actions: {
    /**
     * 初始化 WebSocket
     */
    initWebSocket(url, token) {
      if (this.socket) {
        this.socket.close()
      }
      
      this.socket = uni.connectSocket({
        url: `${url}?token=${token}`,
        success: () => {
          console.log('WebSocket 连接成功')
        }
      })
      
      // 监听连接打开
      this.socket.onOpen(() => {
        this.isConnected = true
        console.log('WebSocket 已连接')
      })
      
      // 监听消息接收
      this.socket.onMessage((res) => {
        const data = JSON.parse(res.data)
        this.handleMessage(data)
      })
      
      // 监听连接关闭
      this.socket.onClose(() => {
        this.isConnected = false
        console.log('WebSocket 已断开')
        
        // 5秒后尝试重连
        setTimeout(() => {
          if (!this.isConnected) {
            this.initWebSocket(url, token)
          }
        }, 5000)
      })
      
      // 监听错误
      this.socket.onError((err) => {
        console.error('WebSocket 错误:', err)
        this.isConnected = false
      })
    },
    
    /**
     * 处理接收到的消息
     */
    handleMessage(data) {
      const { type, message } = data
      
      if (type === 'message') {
        // 新消息
        const conversationId = message.conversationId
        if (!this.messages[conversationId]) {
          this.messages[conversationId] = []
        }
        this.messages[conversationId].push(message)
        
        // 更新会话列表
        const conversation = this.conversationList.find(c => c.id === conversationId)
        if (conversation) {
          conversation.lastMessage = message
          conversation.lastTime = message.createTime
          if (message.fromUserId !== this.currentConversation?.otherUserId) {
            conversation.unreadCount = (conversation.unreadCount || 0) + 1
          }
        }
      }
    },
    
    /**
     * 发送消息
     */
    sendMessage(message) {
      if (!this.isConnected) {
        uni.showToast({
          title: '连接已断开',
          icon: 'none'
        })
        return
      }
      
      this.socket.send({
        data: JSON.stringify(message)
      })
    },
    
    /**
     * 关闭连接
     */
    closeSocket() {
      if (this.socket) {
        this.socket.close()
        this.socket = null
        this.isConnected = false
      }
    }
  }
})
