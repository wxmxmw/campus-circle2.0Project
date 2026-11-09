import request from '@/utils/request'

export const errandApi = {
  // 获取列表
  getList(params) {
    return request.get('/errands', params)
  },
  
  // 获取详情
  getDetail(id) {
    return request.get(`/errands/${id}`)
  },
  
  // 发布
  create(data) {
    return request.post('/errands', data)
  },
  
  // 接单
  accept(id) {
    return request.post(`/errands/${id}/accept`)
  },
  
  // 完成
  complete(id) {
    return request.post(`/errands/${id}/complete`)
  },
  
  // 取消
  cancel(id) {
    return request.post(`/errands/${id}/cancel`)
  }
}
