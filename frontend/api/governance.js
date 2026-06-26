import request from '@/utils/request'

export const governanceApi = {
  runMonitor(data = {}) {
    return request.post('/admin/governance/monitor/run', data, { timeout: 180000 })
  },

  startMonitor(data = {}) {
    return request.post('/admin/governance/monitor/start', data, { timeout: 30000 })
  },

  getMonitorRun(runId) {
    return request.get(`/admin/governance/monitor/runs/${runId}`)
  },

  getEventList(params = {}) {
    return request.get('/admin/governance/events', params)
  },

  getEventDetail(id) {
    return request.get(`/admin/governance/events/${id}`)
  },

  getSnapshots(id) {
    return request.get(`/admin/governance/events/${id}/snapshots`)
  },

  reanalyzeEvent(id) {
    return request.post(`/admin/governance/events/${id}/reanalyze`, {}, { timeout: 180000 })
  },

  recordAction(id, data) {
    return request.post(`/admin/governance/events/${id}/actions`, data)
  },

  mergeEvents(data) {
    return request.post('/admin/governance/events/merge', data)
  },

  clearEvents() {
    return request.post('/admin/governance/events/purge-all')
  },

  closeEvent(id, note) {
    return request.post(`/admin/governance/events/${id}/close`, { note }, { timeout: 120000 })
  },

  getPostmortem(id) {
    return request.get(`/admin/governance/events/${id}/postmortem`, {}, { timeout: 120000 })
  },

  getOfficialData(params = {}) {
    return request.get('/info-center/notices', params)
  },

  getOfficialDataCategories() {
    return request.get('/info-center/notices/categories')
  }
}
