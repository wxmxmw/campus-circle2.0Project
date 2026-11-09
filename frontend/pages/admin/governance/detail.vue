<template>
  <view class="page">
    <view class="header">
      <view class="status-bar"></view>
      <view class="nav">
        <view class="nav-btn" @click="goBack">‹</view>
        <view class="nav-title">Event details</view>
        <view class="nav-btn small" @click="refreshDetail">Refresh</view>
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="loading" class="state-card">Loading details...</view>

      <template v-else>
        <view class="overview-card">
          <view class="top-row">
            <view class="badge-group">
              <text class="risk-badge" :class="(overview.riskLevel || 'LOW').toLowerCase()">{{ overview.riskLevel }}</text>
              <text class="plain-badge">{{ overview.status }}</text>
              <text class="plain-badge rumor">{{ overview.rumorStatus }}</text>
            </view>
            <text class="code">{{ overview.eventCode }}</text>
          </view>
          <text class="title">{{ overview.title }}</text>
          <text class="summary">{{ overview.summary || 'No event summary.' }}</text>
          <view class="stats">
            <view class="stat"><text class="num">{{ overview.riskScore || 0 }}</text><text class="label">Risk score</text></view>
            <view class="stat"><text class="num">{{ overview.sourceCount || 0 }}</text><text class="label">Signals</text></view>
            <view class="stat"><text class="num">{{ overview.reportCount || 0 }}</text><text class="label">Reports</text></view>
            <view class="stat"><text class="num">{{ overview.negativeRatio || 0 }}%</text><text class="label">Negative share</text></view>
          </view>
          <view class="timeline-meta">
            <text>Started：{{ formatDate(overview.startedAt) }}</text>
            <text>Last active：{{ formatDate(overview.lastActiveAt) }}</text>
          </view>
        </view>

        <view class="action-panel">
          <button class="action-btn primary" :loading="reanalyzing" :disabled="reanalyzing" @click="handleReanalyze">Analyze again</button>
          <button class="action-btn" @click="handleManualAction">Record action</button>
          <button class="action-btn" @click="handleClose">Close event</button>
          <button class="action-btn" :loading="postmortemLoading" :disabled="postmortemLoading" @click="loadPostmortem">Generate review</button>
        </view>

        <view class="section-card">
          <text class="section-title">Latest snapshot</text>
          <text class="block-title">Opinion summary</text>
          <text class="block-text">{{ latestSnapshot.opinionSummary || 'None' }}</text>
          <text class="block-title">Demand summary</text>
          <text class="block-text">{{ latestSnapshot.demandsSummary || 'None' }}</text>

          <view class="chip-row">
            <text v-for="item in latestSituation.keywords || []" :key="item" class="chip">{{ item }}</text>
          </view>

          <view class="snapshot-grid">
            <view class="snapshot-box">
              <text class="snapshot-label">Stance distribution</text>
              <text class="snapshot-value">Oppose {{ latestSituation.stanceDistribution?.oppose || 0 }} / Neutral {{ latestSituation.stanceDistribution?.neutral || 0 }} / Support {{ latestSituation.stanceDistribution?.support || 0 }}</text>
            </view>
            <view class="snapshot-box">
              <text class="snapshot-label">Verification result</text>
              <text class="snapshot-value">{{ latestRumor.conclusion || 'None' }}</text>
            </view>
          </view>

          <view class="sub-section">
            <text class="sub-title">Suggested actions</text>
            <view v-if="recommendedActions.length === 0" class="empty-line">No suggested actions</view>
            <view v-for="action in recommendedActions" :key="`${action.type}-${action.priority}`" class="suggestion-item">
              <view class="suggestion-main">
                <text class="suggestion-type">{{ action.type }}</text>
                <text class="suggestion-reason">{{ action.reason }}</text>
              </view>
              <button class="mini-btn" @click="applySuggestedAction(action)">Apply</button>
            </view>
          </view>
        </view>

        <view class="section-card">
          <text class="section-title">Evidence and verification</text>
          <view v-if="evidenceList.length === 0" class="empty-line">No verified evidence yet.</view>
          <view v-for="item in evidenceList" :key="item.id" class="evidence-item">
            <text class="evidence-title">{{ item.title || item.evidenceType }}</text>
            <text class="evidence-text">{{ item.excerpt || 'No excerpt' }}</text>
            <text class="evidence-meta">{{ item.evidenceType }} · Confidence {{ item.confidence || 0 }}</text>
          </view>
        </view>

        <view class="section-card">
          <text class="section-title">Related signals</text>
          <view v-for="item in signals" :key="item.id" class="signal-item">
            <view class="signal-top">
              <text class="signal-type">{{ item.signalType }}</text>
              <text class="signal-time">{{ formatDate(item.signalTime) }}</text>
            </view>
            <text class="signal-content">{{ item.contentText }}</text>
          </view>
        </view>

        <view class="section-card">
          <text class="section-title">Action log</text>
          <view v-if="actions.length === 0" class="empty-line">No administrator actions yet.</view>
          <view v-for="item in actions" :key="item.id" class="action-log">
            <text class="log-title">{{ item.actionType }}</text>
            <text class="log-note">{{ item.actionNote || 'No note' }}</text>
            <text class="log-meta">{{ formatDate(item.createdAt) }} · {{ item.fromAgent ? 'Suggested by AI' : 'Manual entry' }}</text>
          </view>
        </view>

        <view class="section-card">
          <text class="section-title">Snapshot history</text>
          <view v-for="item in snapshots" :key="item.id" class="snapshot-item">
            <view class="snapshot-top">
              <text class="snapshot-version">V{{ item.versionNo }}</text>
              <text class="snapshot-time">{{ formatDate(item.createdAt) }}</text>
            </view>
            <text class="snapshot-desc">{{ item.opinionSummary || 'No summary' }}</text>
          </view>
        </view>

        <view class="section-card">
          <view class="section-row">
            <text class="section-title">Event review</text>
            <button class="mini-btn outline" :loading="postmortemLoading" :disabled="postmortemLoading" @click="loadPostmortem">Refresh review</button>
          </view>
          <view v-if="!postmortem" class="empty-line">A full review can be generated after closing the event; a draft is available now.</view>
          <template v-else>
            <text class="block-text">{{ postmortem.summary }}</text>
            <text class="sub-title">Key moments</text>
            <view v-for="(item, index) in postmortem.timeline || []" :key="`${item.time}-${index}`" class="timeline-item">
              <text class="timeline-time">{{ formatDate(item.time) }}</text>
              <text class="timeline-content">{{ item.type }} · {{ item.content }}</text>
            </view>
            <text class="sub-title">Lessons</text>
            <text v-for="item in postmortem.lessons || []" :key="item" class="lesson-item">· {{ item }}</text>
          </template>
        </view>
      </template>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { governanceApi } from '@/api/governance'

const eventId = ref('')
const loading = ref(false)
const reanalyzing = ref(false)
const postmortemLoading = ref(false)
const overview = ref({})
const latestSnapshot = ref({})
const snapshots = ref([])
const signals = ref([])
const actions = ref([])
const evidenceList = ref([])
const postmortem = ref(null)

const latestSituation = computed(() => latestSnapshot.value?.situation || {})
const latestRumor = computed(() => latestSnapshot.value?.rumor || {})
const recommendedActions = computed(() => latestSnapshot.value?.suggestion?.recommendedActions || [])

onLoad(async (options) => {
  eventId.value = options?.id || ''
  await refreshDetail()
})

const refreshDetail = async () => {
  if (!eventId.value) return
  try {
    loading.value = true
    const res = await governanceApi.getEventDetail(eventId.value)
    overview.value = res.data?.overview || {}
    latestSnapshot.value = res.data?.latestSnapshot || {}
    snapshots.value = res.data?.snapshots || []
    signals.value = res.data?.signals || []
    actions.value = res.data?.actions || []
    evidenceList.value = res.data?.evidence || []
    if (overview.value?.status === 'CLOSED') {
      await loadPostmortem(false)
    }
  } catch (error) {
    uni.showToast({ title: error.message || 'Loading failed', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const handleReanalyze = async () => {
  try {
    reanalyzing.value = true
    uni.showLoading({ title: 'Analyzing...', mask: true })
    await governanceApi.reanalyzeEvent(eventId.value)
    uni.showToast({ title: 'Analysis complete', icon: 'success' })
    await refreshDetail()
  } catch (error) {
    uni.showToast({ title: error.message || 'Analysis failed', icon: 'none' })
  } finally {
    reanalyzing.value = false
    uni.hideLoading()
  }
}

const handleManualAction = () => {
  const options = ['OBSERVE', 'MANUAL_REVIEW', 'CLARIFY', 'FAST_HANDLE_REPORTS', 'ESCALATE_ADMIN']
  uni.showActionSheet({
    itemList: options,
    success: ({ tapIndex }) => {
      const actionType = options[tapIndex]
      uni.showModal({
        title: `Record ${actionType}`,
        editable: true,
        placeholderText: 'Describe the action',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await governanceApi.recordAction(eventId.value, {
              actionType,
              actionNote: res.content || `${actionType} - Manual administrator note`
            })
            uni.showToast({ title: 'Recorded', icon: 'success' })
            await refreshDetail()
          } catch (error) {
            uni.showToast({ title: error.message || 'Could not record', icon: 'none' })
          }
        }
      })
    }
  })
}

const applySuggestedAction = async (action) => {
  try {
    await governanceApi.recordAction(eventId.value, {
      actionType: action.type,
      actionNote: action.reason,
      fromAgent: true
    })
    uni.showToast({ title: 'Suggestion applied', icon: 'success' })
    await refreshDetail()
  } catch (error) {
    uni.showToast({ title: error.message || 'Action failed', icon: 'none' })
  }
}

const handleClose = () => {
  uni.showModal({
    title: 'Close event',
    editable: true,
    placeholderText: 'Explain why the event is closed',
    success: async (res) => {
      if (!res.confirm) return
      try {
        uni.showLoading({ title: 'Closing...' })
        const response = await governanceApi.closeEvent(eventId.value, res.content || 'Administrator confirmed resolution')
        postmortem.value = response.data || null
        uni.showToast({ title: 'Event closed', icon: 'success' })
        await refreshDetail()
      } catch (error) {
        uni.showToast({ title: error.message || 'Could not close', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const loadPostmortem = async (withToast = true) => {
  try {
    postmortemLoading.value = true
    const res = await governanceApi.getPostmortem(eventId.value)
    postmortem.value = res.data || null
    if (withToast) {
      uni.showToast({ title: 'Review updated', icon: 'success' })
    }
  } catch (error) {
    if (withToast) {
      uni.showToast({ title: error.message || 'Could not generate review', icon: 'none' })
    }
  } finally {
    postmortemLoading.value = false
  }
}

const formatDate = (value) => {
  if (!value || value === 'null') return 'None'
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
.page { min-height: 100vh; background: linear-gradient(180deg, #f5f5f3 0%, #ececea 45%, #f3f3f1 100%); }
.header { position: fixed; top: 0; left: 0; right: 0; z-index: 10; background: repeating-linear-gradient(135deg, rgba(255,255,255,.1) 0, rgba(255,255,255,.1) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #171717 0%, #3f3f3b 100%); color: #fff; box-shadow: 0 18rpx 44rpx rgba(17, 17, 17, .22); }
.status-bar { height: var(--status-bar-height); }
.nav { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 24rpx 24rpx; }
.nav-btn { width: 80rpx; height: 64rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.12); font-size: 38rpx; }
.nav-btn.small { width: 108rpx; font-size: 24rpx; }
.nav-title { font-size: 34rpx; font-weight: 700; }
.content { height: 100vh; padding: calc(var(--status-bar-height) + 120rpx) 24rpx 26rpx; box-sizing: border-box; }
.overview-card, .section-card, .state-card, .action-panel { border-radius: 28rpx; border: 1rpx solid rgba(17, 17, 17, .08); box-shadow: 0 18rpx 48rpx rgba(17, 17, 17, .06); }
.overview-card { padding: 28rpx; background: linear-gradient(145deg, rgba(255,255,255,.97), rgba(247,247,245,.93)), repeating-linear-gradient(125deg, rgba(17,17,17,.02) 0, rgba(17,17,17,.02) 2rpx, transparent 2rpx, transparent 24rpx); }
.top-row, .section-row, .signal-top, .snapshot-top { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; }
.badge-group { display: flex; flex-wrap: wrap; gap: 10rpx; }
.risk-badge, .plain-badge { padding: 8rpx 14rpx; border-radius: 999rpx; font-size: 22rpx; font-weight: 700; }
.risk-badge.low { background: rgba(78,163,96,.14); color: #236634; }
.risk-badge.medium { background: rgba(233,162,48,.18); color: #935d0d; }
.risk-badge.high { background: rgba(198,76,58,.16); color: #9b3122; }
.plain-badge { background: rgba(17,17,17,.05); color: #3d3d39; }
.plain-badge.rumor { background: rgba(17,17,17,.08); color: #555550; }
.code { font-size: 22rpx; color: #8d8d87; }
.title { display: block; margin-top: 18rpx; font-size: 38rpx; line-height: 1.4; color: #171717; font-weight: 700; }
.summary { display: block; margin-top: 16rpx; font-size: 26rpx; line-height: 1.72; color: #666660; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; margin-top: 22rpx; }
.stat { padding: 20rpx 14rpx; border-radius: 22rpx; background: rgba(17,17,17,.03); text-align: center; border: 1rpx solid rgba(17,17,17,.06); }
.num { display: block; font-size: 28rpx; font-weight: 700; color: #171717; }
.label { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8d8d87; }
.timeline-meta { display: flex; flex-direction: column; gap: 8rpx; margin-top: 22rpx; font-size: 22rpx; color: #8d8d87; }
.action-panel { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16rpx; padding: 20rpx; margin-top: 18rpx; margin-bottom: 18rpx; background: rgba(255,255,255,.78); }
.action-btn { height: 76rpx; line-height: 76rpx; border-radius: 999rpx; margin: 0; background: rgba(17,17,17,.04); color: #2a2a28; font-size: 26rpx; border: 1rpx solid rgba(17,17,17,.08); }
.action-btn.primary { background: repeating-linear-gradient(135deg, rgba(255,255,255,.14) 0, rgba(255,255,255,.14) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #1c1c1b 0%, #454540 100%); color: #fff; box-shadow: 0 16rpx 32rpx rgba(17,17,17,.16); }
.action-btn::after { border: none; }
.section-card { margin-top: 18rpx; padding: 26rpx; background: linear-gradient(180deg, rgba(255,255,255,.96), rgba(247,247,245,.94)); }
.section-title { font-size: 31rpx; font-weight: 700; color: #171717; }
.block-title, .sub-title { display: block; margin-top: 18rpx; margin-bottom: 10rpx; font-size: 25rpx; color: #2f2f2c; font-weight: 700; }
.block-text, .snapshot-desc, .signal-content, .log-note, .evidence-text, .timeline-content { display: block; font-size: 25rpx; line-height: 1.72; color: #666660; }
.chip-row { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 18rpx; }
.chip { padding: 10rpx 16rpx; border-radius: 999rpx; background: rgba(17,17,17,.04); color: #4c4c48; font-size: 23rpx; border: 1rpx solid rgba(17,17,17,.06); }
.snapshot-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; margin-top: 18rpx; }
.snapshot-box { padding: 18rpx; border-radius: 22rpx; background: rgba(17,17,17,.03); border: 1rpx solid rgba(17,17,17,.06); }
.snapshot-label { display: block; font-size: 22rpx; color: #8d8d87; }
.snapshot-value { display: block; margin-top: 10rpx; font-size: 24rpx; line-height: 1.65; color: #2f2f2c; }
.sub-section { margin-top: 20rpx; }
.empty-line { margin-top: 18rpx; font-size: 25rpx; color: #8d8d87; }
.suggestion-item, .evidence-item, .signal-item, .action-log, .snapshot-item, .timeline-item { padding: 20rpx 0; border-bottom: 1rpx dashed rgba(17,17,17,.1); }
.suggestion-item:last-child, .evidence-item:last-child, .signal-item:last-child, .action-log:last-child, .snapshot-item:last-child, .timeline-item:last-child { border-bottom: none; }
.suggestion-item { display: flex; justify-content: space-between; gap: 18rpx; }
.suggestion-main { flex: 1; }
.suggestion-type { display: block; font-size: 24rpx; color: #1f1f1d; font-weight: 700; }
.suggestion-reason, .evidence-meta, .log-meta, .snapshot-time, .signal-time, .timeline-time { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8d8d87; line-height: 1.6; }
.mini-btn { margin: 0; padding: 0 26rpx; height: 58rpx; line-height: 58rpx; border-radius: 999rpx; background: rgba(17,17,17,.04); color: #2f2f2c; font-size: 23rpx; border: 1rpx solid rgba(17,17,17,.08); }
.mini-btn.outline { background: transparent; }
.mini-btn::after { border: none; }
.evidence-title, .signal-type, .log-title, .snapshot-version { display: block; font-size: 24rpx; color: #1f1f1d; font-weight: 700; }
.lesson-item { display: block; margin-top: 10rpx; font-size: 24rpx; line-height: 1.65; color: #666660; }
.state-card { padding: 48rpx 24rpx; text-align: center; background: rgba(255,255,255,.82); color: #8d8d87; font-size: 28rpx; }
</style>
