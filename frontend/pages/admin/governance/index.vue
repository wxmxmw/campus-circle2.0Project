<template>
  <view class="page">
    <view class="header">
      <view class="status-bar"></view>
      <view class="nav">
        <view class="nav-btn" @click="goBack">‹</view>
        <view class="nav-title">
          <text class="title">Community moderation</text>
          <text class="subtitle">Community moderation agents</text>
        </view>
        <view class="nav-btn ghost" @click="openReports">Reports</view>
      </view>
    </view>

    <scroll-view scroll-y class="content" @scrolltolower="loadEvents">
      <view class="hero-card">
        <view class="hero-copy">
          <text class="hero-title">Run community monitoring</text>
          <text class="hero-text">
            Scan posts, comments, and reports to cluster signals, analyze risk, verify rumors, and suggest actions.
            Agent progress and results appear here.
          </text>
        </view>

        <view class="hero-actions">
          <button class="primary-btn" :loading="running" :disabled="running" @click="handleRunMonitor">Run monitoring</button>
          <button class="secondary-btn" :disabled="running" @click="openOfficialData">Official data</button>
        </view>

        <view class="hero-actions compact">
          <button class="secondary-btn" :disabled="running" @click="toggleMergeMode">
            {{ mergeMode ? 'Exit merge mode' : 'Merge events' }}
          </button>
          <button class="secondary-btn" :disabled="running || !mergeMode" @click="submitMerge">Confirm merge</button>
        </view>

        <view class="hero-actions compact">
          <button class="danger-btn" :disabled="running" @click="handleClearEvents">Clear events</button>
        </view>

        <view v-if="currentRun" class="monitor-panel" :class="stageStatusClass(currentRun.status)">
          <view class="monitor-top">
            <text class="monitor-title">Monitoring in progress</text>
            <text class="monitor-tag">{{ currentRun?.status || 'RUNNING' }}</text>
          </view>
          <text class="monitor-text">{{ currentRun?.message || 'Waiting for monitoring status...' }}</text>
          <view class="stage-list">
            <view
              v-for="item in monitorDisplayStages"
              :key="item.key"
              class="stage-item"
              :class="stageStatusClass(item.status)"
            >
              <text class="stage-dot"></text>
              <view class="stage-copy">
                <text class="stage-name">{{ item.label }}</text>
                <text class="stage-status">{{ stageStatusText(item.status) }}</text>
                <text v-if="item.message" class="stage-message">{{ item.message }}</text>
                <text
                  v-for="(line, index) in buildStageLines(item)"
                  :key="`${item.key}-${index}`"
                  class="stage-message subtle"
                >{{ line }}</text>
              </view>
            </view>
          </view>
        </view>

        <view v-else-if="runSummary" class="run-summary">
          <text>
            Last scan checked {{ runSummary.scannedSignalCount || 0 }} items, found {{ runSummary.newSignalCount || 0 }} new signals, affected
            {{ (runSummary.affectedEventIds || []).length }} events, and completed
            {{ runSummary.reanalyzedCount || 0 }} event analyses.
          </text>
        </view>

        <view v-if="mergeMode" class="merge-panel">
          <text class="merge-title">Event merge mode</text>
          <text class="merge-text">
            Select two events below, confirm the merge, then choose which event to keep.
          </text>
          <text class="merge-count">Selected {{ selectedMergeIds.length }}/2 events</text>
        </view>
      </view>

      <view v-if="workflowRuns.length > 0" class="workflow-card">
        <view class="section-head">
          <text class="section-title">Agent workflow</text>
          <text class="section-subtitle">Track each event and its agent results</text>
        </view>

        <view v-for="workflow in workflowRuns" :key="workflow.eventId" class="workflow-event">
          <view class="workflow-event-top">
            <view>
              <text class="workflow-event-title">{{ workflow.title || `Event #${workflow.eventId}` }}</text>
              <text class="workflow-event-meta">
                Event #{{ workflow.eventId }} · Status {{ workflow.status || '--' }} · Risk {{ workflow.riskLevel || '--' }} · Verification {{ workflow.rumorStatus || '--' }}
              </text>
            </view>
            <text class="workflow-version">V{{ workflow.snapshotVersion || 1 }}</text>
          </view>

          <view class="agent-list">
            <view v-for="agent in workflow.agents || []" :key="agent.key" class="agent-item" :class="agentStatusClass(agent.status)">
              <view class="agent-header">
                <text class="agent-name">{{ agent.label }}</text>
                <text class="agent-role">{{ agent.role }}</text>
              </view>
              <view class="agent-meta">
                <text class="agent-badge">{{ stageStatusText(agent.status) }}</text>
              </view>
              <text class="agent-summary">{{ agent.summary || 'Waiting' }}</text>
              <view class="agent-lines">
                <text
                  v-for="(line, index) in buildAgentLines(agent)"
                  :key="`${agent.key}-${index}`"
                  class="agent-line"
                >{{ line }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="filters">
        <view class="filter-pill" @click="selectStatus">
          <text>Status</text>
          <text class="pill-value">{{ filterLabel(statusOptions, filters.status, 'All') }}</text>
        </view>
        <view class="filter-pill" @click="selectRisk">
          <text>Risk</text>
          <text class="pill-value">{{ filterLabel(riskOptions, filters.riskLevel, 'All') }}</text>
        </view>
        <view class="filter-pill" @click="selectRumor">
          <text>Verification</text>
          <text class="pill-value">{{ filterLabel(rumorOptions, filters.rumorStatus, 'All') }}</text>
        </view>
      </view>

      <view v-if="loading && events.length === 0" class="state-card">Loading events...</view>
      <view v-else-if="events.length === 0" class="state-card">No matching moderation events.</view>

      <view v-else class="event-list">
        <view
          v-for="item in events"
          :key="item.id"
          class="event-card"
          :class="{ selectable: mergeMode, selected: selectedMergeIds.includes(item.id) }"
          @click="handleCardClick(item)"
        >
          <view class="event-top">
            <view class="badges">
              <text class="risk-badge" :class="(item.riskLevel || 'LOW').toLowerCase()">{{ item.riskLevel }}</text>
              <text class="status-badge">{{ item.status }}</text>
              <text class="rumor-badge">{{ item.rumorStatus }}</text>
            </view>
            <view class="event-top-right">
              <text v-if="mergeMode" class="select-indicator">{{ selectedMergeIds.includes(item.id) ? 'Selected' : 'Select' }}</text>
              <text class="time">{{ formatDate(item.lastActiveAt) }}</text>
            </view>
          </view>
          <text class="event-title">{{ item.title }}</text>
          <text class="event-summary">{{ item.summary || 'Waiting for analysis.' }}</text>
          <view class="metrics">
            <text>Risk score {{ item.riskScore || 0 }}</text>
            <text>Signals {{ item.sourceCount || 0 }}</text>
            <text>Reports {{ item.reportCount || 0 }}</text>
            <text>Negative {{ item.negativeRatio || 0 }}%</text>
          </view>
          <view class="suggestion-row">
            <text class="suggestion-label">Current suggestion</text>
            <text class="suggestion-value">{{ item.currentSuggestion || 'OBSERVE' }}</text>
          </view>
        </view>
      </view>

      <view v-if="loadingMore" class="load-more loading-more">Loading more events...</view>
      <view v-else-if="hasMore" class="load-more" @click="loadEvents">Load more</view>
      <view style="height: 40rpx;"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { governanceApi } from '@/api/governance'

const events = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const running = ref(false)
const runSummary = ref(null)
const page = ref(1)
const hasMore = ref(false)
const mergeMode = ref(false)
const selectedMergeIds = ref([])
const currentRunId = ref('')
const currentRun = ref(null)
let pollTimer = null

const monitorStageMeta = [
  { key: 'ingest', label: 'Signal collection agent' },
  { key: 'cluster', label: 'Event clustering agent' }
]

const statusOptions = [
  { label: 'All', value: '' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Observing', value: 'OBSERVING' },
  { label: 'Action needed', value: 'ACTION_PENDING' },
  { label: 'Escalated', value: 'ESCALATED' },
  { label: 'Closed', value: 'CLOSED' }
]

const riskOptions = [
  { label: 'All', value: '' },
  { label: 'High risk', value: 'HIGH' },
  { label: 'Medium risk', value: 'MEDIUM' },
  { label: 'Low risk', value: 'LOW' }
]

const rumorOptions = [
  { label: 'All', value: '' },
  { label: 'Confirmed', value: 'CONFIRMED' },
  { label: 'Unverified', value: 'UNVERIFIED' },
  { label: 'Insufficient evidence', value: 'INSUFFICIENT' },
  { label: 'Refuted', value: 'REFUTED' }
]

const filters = reactive({
  status: '',
  riskLevel: '',
  rumorStatus: ''
})

const monitorDisplayStages = computed(() => {
  const stages = currentRun.value?.stages || []
  return monitorStageMeta.map(meta => {
    const matched = stages.find(item => item.key === meta.key) || {}
    return {
      ...meta,
      ...matched,
      status: matched.status || 'PENDING',
      output: matched.output || {}
    }
  })
})

const workflowRuns = computed(() => {
  if (currentRun.value?.workflowRuns?.length) {
    return currentRun.value.workflowRuns
  }
  return runSummary.value?.workflowRuns || []
})

onLoad(() => {
  uni.setNavigationBarTitle({ title: 'Community moderation' })
})

onShow(() => {
  resetAndLoad()
})

onPullDownRefresh(async () => {
  await resetAndLoad()
  uni.stopPullDownRefresh()
})

onBeforeUnmount(() => {
  stopPolling()
})

const stopPolling = () => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

const schedulePoll = (delay = 1200) => {
  stopPolling()
  pollTimer = setTimeout(fetchMonitorRun, delay)
}

const normalizeMonitorRun = (payload = {}) => ({
  ...payload,
  stages: Array.isArray(payload.stages) ? payload.stages : [],
  workflowRuns: Array.isArray(payload.workflowRuns) ? payload.workflowRuns : [],
  result: payload.result || {}
})

const resetAndLoad = async () => {
  page.value = 1
  events.value = []
  hasMore.value = false
  await loadEvents(true)
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

const loadEvents = async (force = false) => {
  const shouldForce = force === true
  if (loading.value || loadingMore.value) return
  if (!shouldForce && !hasMore.value && page.value > 1) return

  const isAppending = !shouldForce && events.value.length > 0 && page.value > 1

  try {
    if (isAppending) {
      loadingMore.value = true
    } else {
      loading.value = true
    }

    const res = await governanceApi.getEventList({
      page: page.value,
      pageSize: 10,
      status: filters.status,
      riskLevel: filters.riskLevel,
      rumorStatus: filters.rumorStatus
    })

    const list = res.data?.list || []
    if (page.value === 1) {
      events.value = list
      hasMore.value = !!res.data?.hasMore
    } else {
      const appended = mergeUniqueById(events.value, list)
      events.value = [...events.value, ...appended]
      hasMore.value = !!res.data?.hasMore && appended.length > 0
    }

    if (hasMore.value) {
      page.value += 1
    }
  } catch (error) {
    uni.showToast({ title: error.message || 'Loading failed', icon: 'none' })
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const handleRunMonitor = () => {
  uni.showActionSheet({
    itemList: ['Scan the last 24 hours', 'Scan the last 48 hours', 'Scan the last 72 hours', 'Scan the last two weeks'],
    success: async ({ tapIndex }) => {
      const windowHours = [24, 48, 72, 336][tapIndex] || 24
      try {
        running.value = true
        runSummary.value = null
        currentRun.value = null
        currentRunId.value = ''
        stopPolling()

        const res = await governanceApi.startMonitor({
          windowHours,
          includePosts: true,
          includeComments: true,
          includeReports: true,
          maxSignals: 120
        })

        currentRunId.value = res.data?.runId || ''
        currentRun.value = normalizeMonitorRun({
          runId: currentRunId.value,
          status: res.data?.status || 'PENDING',
          message: res.data?.message || 'Monitoring started',
          stages: [],
          workflowRuns: []
        })
        await fetchMonitorRun()
      } catch (error) {
        running.value = false
        uni.showToast({ title: error.message || 'Run failed', icon: 'none' })
      }
    }
  })
}

const fetchMonitorRun = async () => {
  if (!currentRunId.value) return
  try {
    const res = await governanceApi.getMonitorRun(currentRunId.value)
    currentRun.value = normalizeMonitorRun(res.data || {})

    if (currentRun.value.status === 'COMPLETED') {
      running.value = false
      runSummary.value = currentRun.value.result || {}
      stopPolling()
      uni.showToast({ title: 'Monitoring complete', icon: 'success' })
      await resetAndLoad()
      return
    }

    if (currentRun.value.status === 'FAILED') {
      running.value = false
      stopPolling()
      uni.showToast({ title: currentRun.value.message || 'Monitoring failed', icon: 'none' })
      return
    }

    schedulePoll()
  } catch (error) {
    running.value = false
    stopPolling()
    uni.showToast({ title: error.message || 'Could not get monitoring status', icon: 'none' })
  }
}

const buildStageLines = (stage) => {
  const output = stage.output || {}
  if (stage.key === 'ingest') {
    return [
      output.scannedSignalCount != null ? `Scanned ${output.scannedSignalCount} items` : '',
      output.newSignalCount != null ? `${output.newSignalCount} new moderation signals` : '',
      output.windowHours != null ? `Monitoring window: ${output.windowHours} hours` : ''
    ].filter(Boolean)
  }

  if (stage.key === 'cluster') {
    return [
      output.clusteredSignalCount != null ? `Clustered ${output.clusteredSignalCount} signals` : '',
      output.affectedEventCount != null ? `Affected ${output.affectedEventCount} events` : '',
      Array.isArray(output.affectedEventIds) && output.affectedEventIds.length
        ? `Event IDs：${output.affectedEventIds.slice(0, 6).join('、')}`
        : ''
    ].filter(Boolean)
  }

  return []
}

const buildAgentLines = (agent) => {
  const output = agent.output || {}

  if (agent.key === 'situation') {
    return [
      output.demandsSummary || '',
      output.heatDescription || '',
      output.riskLevel ? `Risk level：${output.riskLevel}` : ''
    ].filter(Boolean)
  }

  if (agent.key === 'rumor') {
    return [
      output.rumorStatus ? `Verification result：${output.rumorStatus}` : '',
      ...(Array.isArray(output.coreClaims) ? output.coreClaims.slice(0, 2).map(item => `Core claim：${item}`) : []),
      ...(Array.isArray(output.conflictPoints) ? output.conflictPoints.slice(0, 2).map(item => `Conflict：${item}`) : [])
    ].filter(Boolean)
  }

  if (agent.key === 'suggestion') {
    return [
      ...(Array.isArray(output.recommendedActions) ? output.recommendedActions.slice(0, 3).map(item => `Suggested actions：${item.type}`) : []),
      ...(Array.isArray(output.humanReviewPoints) ? output.humanReviewPoints.slice(0, 3).map(item => `Review point：${item}`) : [])
    ].filter(Boolean)
  }

  return []
}

const stageStatusText = (status) => {
  switch (status) {
    case 'RUNNING':
      return 'Running'
    case 'COMPLETED':
      return 'Completed'
    case 'SKIPPED':
      return 'Skipped'
    case 'FAILED':
      return 'Failed'
    default:
      return 'Waiting'
  }
}

const stageStatusClass = (status) => {
  return `status-${String(status || 'PENDING').toLowerCase()}`
}

const agentStatusClass = (status) => {
  return `status-${String(status || 'PENDING').toLowerCase()}`
}

const toggleMergeMode = () => {
  mergeMode.value = !mergeMode.value
  selectedMergeIds.value = []
}

const handleCardClick = (item) => {
  if (!mergeMode.value) {
    goDetail(item.id)
    return
  }

  const current = [...selectedMergeIds.value]
  const index = current.indexOf(item.id)
  if (index >= 0) {
    current.splice(index, 1)
    selectedMergeIds.value = current
    return
  }

  if (current.length >= 2) {
    uni.showToast({ title: 'Select at most two events', icon: 'none' })
    return
  }

  current.push(item.id)
  selectedMergeIds.value = current
}

const submitMerge = () => {
  if (!mergeMode.value) {
    uni.showToast({ title: 'Enter merge mode first', icon: 'none' })
    return
  }
  if (selectedMergeIds.value.length !== 2) {
    uni.showToast({ title: 'Select two events first', icon: 'none' })
    return
  }

  const [firstId, secondId] = selectedMergeIds.value
  const firstEvent = events.value.find(item => item.id === firstId)
  const secondEvent = events.value.find(item => item.id === secondId)
  const firstLabel = firstEvent?.title || `Event #${firstId}`
  const secondLabel = secondEvent?.title || `Event #${secondId}`

  uni.showActionSheet({
    itemList: [`Keep ${truncateLabel(firstLabel)}`, `Keep ${truncateLabel(secondLabel)}`],
    success: async ({ tapIndex }) => {
      const sourceEventId = tapIndex === 0 ? secondId : firstId
      const targetEventId = tapIndex === 0 ? firstId : secondId
      try {
        uni.showLoading({ title: 'Merging events...', mask: true })
        await governanceApi.mergeEvents({ sourceEventId, targetEventId })
        uni.showToast({ title: 'Events merged', icon: 'success' })
        mergeMode.value = false
        selectedMergeIds.value = []
        await resetAndLoad()
      } catch (error) {
        uni.showToast({ title: error.message || 'Merge failed', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const handleClearEvents = () => {
  uni.showModal({
    title: 'Clear moderation events',
    content: 'This deletes moderation events, signals, snapshots, evidence, sources, and action logs. Posts, comments, and reports remain. Continue?',
    confirmText: 'Clear events',
    confirmColor: '#b13a2b',
    success: async (res) => {
      if (!res.confirm) return
      try {
        uni.showLoading({ title: 'Clearing...', mask: true })
        const response = await governanceApi.clearEvents()
        currentRun.value = null
        currentRunId.value = ''
        runSummary.value = null
        mergeMode.value = false
        selectedMergeIds.value = []
        stopPolling()
        await resetAndLoad()
        uni.showToast({
          title: `Cleared ${response.data?.clearedEventCount || 0} events`,
          icon: 'success'
        })
      } catch (error) {
        uni.showToast({ title: error.message || 'Could not clear', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const truncateLabel = (value) => {
  if (!value) return ''
  return value.length > 12 ? `${value.slice(0, 12)}...` : value
}

const selectStatus = () => selectOption(statusOptions, 'status')
const selectRisk = () => selectOption(riskOptions, 'riskLevel')
const selectRumor = () => selectOption(rumorOptions, 'rumorStatus')

const selectOption = (options, field) => {
  uni.showActionSheet({
    itemList: options.map(item => (item.value ? `${item.label} (${item.value})` : item.label)),
    success: async ({ tapIndex }) => {
      filters[field] = options[tapIndex]?.value ?? ''
      await resetAndLoad()
    }
  })
}

const filterLabel = (options, value, fallback) => {
  return options.find(item => item.value === value)?.label || fallback
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

const goDetail = (id) => {
  uni.navigateTo({ url: `/pages/admin/governance/detail?id=${id}` })
}

const openReports = () => {
  uni.navigateTo({ url: '/pages/admin/report-list' })
}

const openOfficialData = () => {
  uni.navigateTo({ url: '/pages/admin/governance/official-data' })
}

const goBack = () => {
  uni.navigateBack()
}
</script>

<style scoped>
.page { min-height: 100vh; background: linear-gradient(180deg, #f5f5f3 0%, #ececea 46%, #f3f3f1 100%); }
.header { position: fixed; top: 0; left: 0; right: 0; z-index: 10; background: repeating-linear-gradient(135deg, rgba(255,255,255,.1) 0, rgba(255,255,255,.1) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #171717 0%, #3f3f3b 100%); color: #fff; box-shadow: 0 18rpx 40rpx rgba(17, 17, 17, .2); }
.status-bar { height: var(--status-bar-height); }
.nav { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 26rpx 24rpx; }
.nav-btn { min-width: 92rpx; height: 68rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.12); font-size: 40rpx; }
.nav-btn.ghost { font-size: 24rpx; letter-spacing: 1rpx; }
.nav-title { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.title { font-size: 34rpx; font-weight: 700; }
.subtitle { font-size: 22rpx; opacity: .74; }
.content { height: 100vh; box-sizing: border-box; padding: calc(var(--status-bar-height) + 124rpx) 24rpx 28rpx; }
.hero-card, .workflow-card, .state-card, .event-card, .filters { border-radius: 28rpx; border: 1rpx solid rgba(17, 17, 17, .08); box-shadow: 0 18rpx 48rpx rgba(17, 17, 17, .06); }
.hero-card { padding: 28rpx; background: linear-gradient(145deg, rgba(255,255,255,.97), rgba(247,247,245,.93)), repeating-linear-gradient(125deg, rgba(17,17,17,.02) 0, rgba(17,17,17,.02) 2rpx, transparent 2rpx, transparent 24rpx); margin-bottom: 22rpx; }
.hero-copy { display: flex; flex-direction: column; gap: 12rpx; }
.hero-title { font-size: 34rpx; color: #171717; font-weight: 700; }
.hero-text { font-size: 25rpx; line-height: 1.7; color: #666660; }
.hero-actions { display: flex; gap: 18rpx; margin-top: 22rpx; }
.hero-actions.compact { margin-top: 14rpx; }
.primary-btn, .secondary-btn { flex: 1; height: 78rpx; line-height: 78rpx; margin: 0; border-radius: 999rpx; font-size: 28rpx; }
.primary-btn { background: repeating-linear-gradient(135deg, rgba(255,255,255,.14) 0, rgba(255,255,255,.14) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #1c1c1b 0%, #454540 100%); color: #fff; box-shadow: 0 16rpx 32rpx rgba(17,17,17,.16); }
.secondary-btn { background: rgba(17,17,17,.04); color: #2a2a28; border: 1rpx solid rgba(17,17,17,.08); }
.danger-btn { flex: 1; height: 78rpx; line-height: 78rpx; margin: 0; border-radius: 999rpx; font-size: 28rpx; background: rgba(177,58,43,.08); color: #9b3122; border: 1rpx solid rgba(177,58,43,.22); }
.primary-btn::after, .secondary-btn::after, .danger-btn::after { border: none; }
.run-summary, .merge-panel, .monitor-panel { margin-top: 18rpx; padding: 18rpx 22rpx; border-radius: 20rpx; background: rgba(17,17,17,.04); color: #4c4c48; font-size: 24rpx; line-height: 1.6; border: 1rpx solid rgba(17,17,17,.06); }
.monitor-panel.running { background: linear-gradient(135deg, rgba(17,17,17,.06), rgba(17,17,17,.03)); }
.monitor-panel.status-running,
.monitor-panel.status-pending { background: linear-gradient(135deg, rgba(17,17,17,.06), rgba(17,17,17,.03)); }
.monitor-panel.status-completed { background: linear-gradient(135deg, rgba(240,249,243,.96), rgba(255,255,255,.92)); border-color: rgba(45,132,78,.16); }
.monitor-panel.status-failed { background: linear-gradient(135deg, rgba(255,244,241,.96), rgba(255,255,255,.92)); border-color: rgba(198,76,58,.16); }
.monitor-top { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; }
.monitor-title, .merge-title { font-size: 27rpx; font-weight: 700; color: #171717; }
.monitor-tag { padding: 6rpx 14rpx; border-radius: 999rpx; background: rgba(17,17,17,.08); color: #2f2f2c; font-size: 22rpx; }
.monitor-text, .merge-text { display: block; margin-top: 10rpx; }
.stage-list { display: flex; flex-direction: column; gap: 12rpx; margin-top: 16rpx; }
.stage-item { display: flex; align-items: flex-start; gap: 12rpx; padding: 14rpx 16rpx; border-radius: 20rpx; background: rgba(255,255,255,.74); border: 1rpx solid rgba(17,17,17,.06); }
.stage-item.status-running { border-color: rgba(209, 138, 32, .34); background: rgba(255,248,232,.86); }
.stage-item.status-completed { border-color: rgba(45, 132, 78, .24); background: rgba(240,249,243,.88); }
.stage-item.status-failed { border-color: rgba(198, 76, 58, .24); background: rgba(255,244,241,.88); }
.stage-item.status-skipped { opacity: .86; }
.stage-copy { display: flex; flex-direction: column; gap: 6rpx; flex: 1; }
.stage-dot { width: 14rpx; height: 14rpx; margin-top: 10rpx; border-radius: 50%; background: #1f1f1d; }
.stage-name { font-size: 24rpx; color: #2f2f2c; font-weight: 700; }
.stage-status { font-size: 21rpx; color: #777771; }
.stage-message { font-size: 22rpx; color: #5d5d58; line-height: 1.55; }
.stage-message.subtle { color: #7a7a74; }
.merge-count { display: block; margin-top: 10rpx; font-weight: 700; color: #171717; }
.workflow-card { padding: 26rpx; background: linear-gradient(180deg, rgba(255,255,255,.96), rgba(247,247,245,.94)); margin-bottom: 22rpx; }
.section-head { display: flex; flex-direction: column; gap: 8rpx; }
.section-title { font-size: 31rpx; font-weight: 700; color: #171717; }
.section-subtitle { font-size: 23rpx; color: #777771; line-height: 1.6; }
.workflow-event { margin-top: 22rpx; padding-top: 22rpx; border-top: 1rpx dashed rgba(17,17,17,.1); }
.workflow-event:first-of-type { margin-top: 18rpx; padding-top: 0; border-top: none; }
.workflow-event-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 18rpx; }
.workflow-event-title { display: block; font-size: 28rpx; font-weight: 700; color: #171717; }
.workflow-event-meta { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8d8d87; line-height: 1.5; }
.workflow-version { padding: 8rpx 16rpx; border-radius: 999rpx; background: rgba(17,17,17,.04); color: #3d3d39; font-size: 22rpx; }
.agent-list { display: flex; flex-direction: column; gap: 16rpx; margin-top: 18rpx; }
.agent-item { padding: 20rpx; border-radius: 22rpx; background: rgba(17,17,17,.03); border: 1rpx solid rgba(17,17,17,.06); }
.agent-item.status-running { border-color: rgba(209, 138, 32, .34); background: rgba(255,248,232,.86); }
.agent-item.status-completed { border-color: rgba(45, 132, 78, .22); background: rgba(241,249,243,.86); }
.agent-item.status-failed { border-color: rgba(198, 76, 58, .22); background: rgba(255,244,241,.86); }
.agent-header { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; }
.agent-name { font-size: 25rpx; font-weight: 700; color: #171717; }
.agent-role { font-size: 22rpx; color: #8d8d87; }
.agent-meta { margin-top: 10rpx; }
.agent-badge { display: inline-flex; padding: 6rpx 12rpx; border-radius: 999rpx; background: rgba(17,17,17,.06); color: #4c4c48; font-size: 20rpx; }
.agent-summary { display: block; margin-top: 10rpx; font-size: 24rpx; line-height: 1.68; color: #4c4c48; }
.agent-lines { display: flex; flex-direction: column; gap: 8rpx; margin-top: 12rpx; }
.agent-line { font-size: 22rpx; line-height: 1.6; color: #666660; }
.filters { display: flex; gap: 16rpx; padding: 18rpx; background: rgba(255,255,255,.76); margin-bottom: 22rpx; backdrop-filter: blur(18rpx); -webkit-backdrop-filter: blur(18rpx); }
.filter-pill { flex: 1; display: flex; flex-direction: column; gap: 10rpx; padding: 18rpx; border-radius: 20rpx; background: rgba(17,17,17,.03); color: #666660; font-size: 24rpx; border: 1rpx solid rgba(17,17,17,.06); }
.pill-value { font-size: 28rpx; color: #171717; font-weight: 600; }
.state-card { padding: 48rpx 28rpx; text-align: center; background: rgba(255,255,255,.82); color: #8d8d87; font-size: 28rpx; }
.event-list { display: flex; flex-direction: column; gap: 18rpx; }
.event-card { padding: 26rpx; background: linear-gradient(180deg, rgba(255,255,255,.96), rgba(247,247,245,.94)); }
.event-card.selectable { border-color: rgba(17,17,17,.14); }
.event-card.selected { border-color: rgba(17,17,17,.32); box-shadow: 0 20rpx 44rpx rgba(17,17,17,.12); transform: translateY(-2rpx); }
.event-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18rpx; gap: 18rpx; }
.event-top-right { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; }
.select-indicator { font-size: 22rpx; color: #171717; font-weight: 700; }
.badges { display: flex; flex-wrap: wrap; gap: 10rpx; }
.risk-badge, .status-badge, .rumor-badge { padding: 8rpx 14rpx; border-radius: 999rpx; font-size: 22rpx; font-weight: 600; }
.risk-badge.low { background: rgba(78, 163, 96, .14); color: #236634; }
.risk-badge.medium { background: rgba(233, 162, 48, .18); color: #935d0d; }
.risk-badge.high { background: rgba(198, 76, 58, .16); color: #9b3122; }
.status-badge { background: rgba(17,17,17,.05); color: #3d3d39; }
.rumor-badge { background: rgba(17,17,17,.08); color: #555550; }
.time { font-size: 22rpx; color: #8d8d87; }
.event-title { display: block; font-size: 32rpx; font-weight: 700; color: #171717; line-height: 1.45; }
.event-summary { display: block; margin-top: 14rpx; font-size: 25rpx; line-height: 1.7; color: #666660; }
.metrics { display: flex; flex-wrap: wrap; gap: 18rpx; margin-top: 20rpx; font-size: 23rpx; color: #777771; }
.suggestion-row { margin-top: 18rpx; display: flex; justify-content: space-between; align-items: center; padding-top: 18rpx; border-top: 1rpx dashed rgba(17,17,17,.1); }
.suggestion-label { font-size: 24rpx; color: #777771; }
.suggestion-value { font-size: 26rpx; color: #1f1f1d; font-weight: 700; }
.load-more { margin: 22rpx auto 0; width: 280rpx; text-align: center; padding: 20rpx 0; border-radius: 999rpx; background: rgba(17,17,17,.05); color: #2f2f2c; font-size: 24rpx; border: 1rpx solid rgba(17,17,17,.06); }
.loading-more { pointer-events: none; }
</style>
