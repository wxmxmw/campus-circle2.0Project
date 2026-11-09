<template>
  <view class="publish-container">
    <!-- Description -->
    <view class="form-group">
      <view class="form-item">
        <text class="label">Description</text>
        <textarea
          class="textarea"
          v-model="form.content"
          placeholder="For example: Pick up a parcel at the south gate..."
        />
      </view>
    </view>

    <!-- 地址信息 -->
    <view class="form-group">
      <view class="form-item">
        <text class="label">Pickup location</text>
        <input class="input" v-model="form.pickupAddr" placeholder="Where should it be picked up?" />
      </view>
      <view class="form-item">
        <text class="label">Delivery location</text>
        <input class="input" v-model="form.deliveryAddr" placeholder="Where should it go?" />
      </view>
    </view>

    <!-- 隐私信息 -->
    <view class="form-group">
      <view class="form-item">
        <text class="label">Private details (shown after acceptance)</text>
        <input class="input" v-model="form.hiddenInfo" placeholder="For example: pickup code 12-34-56" />
      </view>
    </view>

    <!-- Due time -->
    <view class="form-group">
      <picker mode="date" :start="startDate" @change="bindDateChange">
        <view class="form-item row">
          <text class="label">Due date</text>
          <view class="picker-val">
            {{ form.deadlineDate || 'Select a date' }} >
          </view>
        </view>
      </picker>
      <picker mode="time" @change="bindTimeChange">
        <view class="form-item row">
          <text class="label">Due time</text>
          <view class="picker-val">
             {{ form.deadlineTime || 'Select a time' }} >
          </view>
        </view>
      </picker>
    </view>

    <!-- 悬赏设置 (重点) -->
    <view class="form-group">
      <view class="form-item">
        <text class="label">Payment method</text>
        <radio-group @change="handleCurrencyChange" class="radio-group">
          <label class="radio-label">
            <radio value="1" color="#3f3f3f" :checked="form.currency === 1" />
            <text>Pay with points</text>
            <text class="balance">(Balance: {{ userStore.userInfo?.points || 0 }})</text>
          </label>
          <label class="radio-label">
            <radio value="2" color="#5a5a5a" :checked="form.currency === 2" />
            <text>Pay in cash (¥)</text>
          </label>
        </radio-group>
      </view>

      <view class="form-item">
        <text class="label">{{ form.currency === 1 ? 'Reward (points)' : 'Reward (CNY)' }}</text>
        <input
          class="input price"
          type="digit"
          v-model="form.bounty"
          :placeholder="form.currency === 1 ? 'Enter points' : '0.00'"
        />
      </view>
    </view>

    <button class="submit-btn" @click="handleSubmit" :loading="loading">Publish</button>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { errandApi } from '@/api/errand'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)

// 获取当前期作为 Picker 最小值
const startDate = new Date().toISOString().slice(0, 10)

const form = reactive({
  content: '',
  pickupAddr: '',
  deliveryAddr: '',
  hiddenInfo: '',
  bounty: '',
  currency: 1, // 默认 1-points
  deadlineDate: '',
  deadlineTime: ''
})

const handleCurrencyChange = (e) => {
  form.currency = parseInt(e.detail.value)
  form.bounty = '' // 切换时清空输入
}

const bindDateChange = (e) => form.deadlineDate = e.detail.value
const bindTimeChange = (e) => form.deadlineTime = e.detail.value

const handleSubmit = async () => {
  if (!form.content || !form.bounty) return uni.showToast({ title: 'Complete all fields', icon: 'none' })
  if (!form.deadlineDate || !form.deadlineTime) return uni.showToast({ title: 'Set a due time', icon: 'none' })

  // 校验pointsBalance
  if (form.currency === 1 && parseInt(form.bounty) > (userStore.userInfo?.points || 0)) {
    return uni.showToast({ title: 'Insufficient points', icon: 'none' })
  }

  // 构造 API 需要的格式
  const submitData = {
    ...form,
    // 合并时间
    deadline: `${form.deadlineDate} ${form.deadlineTime}:00`,
    // 确保数字类型
    bounty: parseFloat(form.bounty)
  }

  loading.value = true
  try {
    const res = await errandApi.create(submitData)
    if (res.code === 200) {
      uni.showToast({ title: 'Post published' })

      // 如果是Pay with points，手动扣除本地 Store 的points，保持 UI 同步
      if (form.currency === 1) {
        userStore.updateUserInfo({
          points: userStore.userInfo.points - parseInt(form.bounty)
        })
      }

      setTimeout(() => {
        // Post published后，Back大厅
        uni.switchTab({ url: '/pages/errand/index' })
      }, 1000)
    }
  } catch (error) {
    uni.showToast({ title: error.message || 'Publishing failed', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.publish-container { min-height: 100vh; background: #F5F5F5; padding: 20rpx; padding-bottom: 100rpx;}
.form-group { background: #fff; border-radius: 12rpx; padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { padding: 30rpx 0; border-bottom: 1rpx solid #eee; display: flex; flex-direction: column; }
.form-item.row { flex-direction: row; justify-content: space-between; align-items: center; }
.form-item:last-child { border-bottom: none; }
.label { font-size: 28rpx; font-weight: bold; margin-bottom: 16rpx; color: #333; }
.input { font-size: 30rpx; height: 60rpx; }
.textarea { width: 100%; height: 160rpx; font-size: 30rpx; }
.price { color: #ff4d4f; font-weight: bold; font-size: 36rpx; }
.submit-btn { margin-top: 60rpx; background: #3f3f3f; color: #fff; border-radius: 40rpx; }

.picker-val { color: #666; font-size: 30rpx; }
.radio-group { display: flex; gap: 40rpx; }
.radio-label { display: flex; align-items: center; font-size: 28rpx; }
.balance { color: #999; font-size: 24rpx; margin-left: 8rpx; }
</style>
