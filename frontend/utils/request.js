import { getApiBaseUrl } from '@/utils/api'

let useMock = false

const mockUrl = '/api'
const timeout = 30000
const tokenKey = 'token'

if (useMock) {
  // #ifdef H5
  import('@/mock/index.js')
  // #endif

  // #ifdef MP || APP-PLUS
  require('@/mock/index.js')
  // #endif
}

const getBaseUrl = () => {
  return useMock ? mockUrl : getApiBaseUrl()
}

const requestInterceptor = (config) => {
  const token = uni.getStorageSync(tokenKey)

  if (token) {
    config.header = {
      ...config.header,
      Authorization: `Bearer ${token}`
    }
  }

  if (config.method === 'GET') {
    config.data = {
      ...config.data,
      _t: Date.now()
    }
  }

  if (process.env.NODE_ENV === 'development') {
    console.log('[request]', config.method, config.url, config.data)
  }

  return config
}

const responseInterceptor = (response) => {
  const { statusCode, data } = response

  if (process.env.NODE_ENV === 'development') {
    console.log('[response]', statusCode, data)
  }

  if (statusCode === 200) {
    if (typeof data === 'string' && /^\s*<!doctype html/i.test(data)) {
      const hint =
        'Request returned HTML (likely hit the H5 dev server instead of backend). ' +
        'Check devBackendOrigin storage or ensure baseURL points to http://localhost:8080/api.'
      uni.showToast({ title: hint, icon: 'none', duration: 2500 })
      return Promise.reject(new Error(hint))
    }

    if (data.code === 0 || data.code === 200) {
      return data
    }

    if (data.code === 401) {
      uni.removeStorageSync(tokenKey)
      uni.reLaunch({ url: '/pages/login/index' })
      return Promise.reject(new Error('Login expired'))
    }

    if (data.code === 403) {
      const token = uni.getStorageSync(tokenKey)
      if (!token) {
        uni.reLaunch({ url: '/pages/login/index' })
        return Promise.reject(new Error('Login required'))
      }
      uni.showToast({ title: data.message || 'Forbidden', icon: 'none', duration: 2000 })
      return Promise.reject(new Error(data.message || 'Forbidden'))
    }

    uni.showToast({
      title: data.message || 'Request failed',
      icon: 'none',
      duration: 2000
    })
    return Promise.reject(new Error(data.message || 'Request failed'))
  }

  if (statusCode === 401) {
    uni.removeStorageSync(tokenKey)
    uni.reLaunch({ url: '/pages/login/index' })
    return Promise.reject(new Error('Unauthorized'))
  }

  uni.showToast({
    title: `Request error ${statusCode}`,
    icon: 'none'
  })
  return Promise.reject(new Error(`HTTP ${statusCode}`))
}

const executeRequest = (config, resolve, reject) => {
  const baseUrl = getBaseUrl()
  const finalUrl = baseUrl + config.url

  if (process.env.NODE_ENV === 'development') {
    console.log('[baseUrl]', baseUrl)
    console.log('[finalUrl]', finalUrl)
  }

  const finalConfig = requestInterceptor({
    url: finalUrl,
    method: config.method || 'GET',
    data: config.data || {},
    header: {
      'Content-Type': 'application/json',
      ...config.header
    },
    timeout: config.timeout || timeout
  })

  uni.request({
    ...finalConfig,
    success: (res) => {
      Promise.resolve(responseInterceptor(res)).then(resolve).catch(reject)
    },
    fail: (err) => {
      console.error('request failed:', err)
      uni.showToast({
        title: 'Network request failed',
        icon: 'none'
      })
      reject(err)
    }
  })
}

const request = (config) => {
  return new Promise((resolve, reject) => {
    executeRequest(config, resolve, reject)
  })
}

export const get = (url, data = {}, config = {}) => {
  return request({
    url,
    method: 'GET',
    data,
    ...config
  })
}

export const post = (url, data = {}, config = {}) => {
  return request({
    url,
    method: 'POST',
    data,
    ...config
  })
}

export const put = (url, data = {}, config = {}) => {
  return request({
    url,
    method: 'PUT',
    data,
    ...config
  })
}

export const del = (url, data = {}, config = {}) => {
  return request({
    url,
    method: 'DELETE',
    data,
    ...config
  })
}

export const upload = (filePath, data = {}) => {
  return new Promise((resolve, reject) => {
    if (useMock) {
      setTimeout(() => {
        resolve({
          url: `https://picsum.photos/400/600?random=${Date.now()}`,
          width: 400,
          height: 600
        })
      }, 500)
      return
    }

    const token = uni.getStorageSync(tokenKey)

    uni.uploadFile({
      url: `${getApiBaseUrl()}/common/upload`,
      filePath,
      name: 'file',
      formData: data,
      header: {
        Authorization: token ? `Bearer ${token}` : ''
      },
      success: (res) => {
        const payload = JSON.parse(res.data)
        if (payload.code === 0 || payload.code === 200) {
          resolve(payload.data)
          return
        }
        uni.showToast({
          title: payload.message || 'Upload failed',
          icon: 'none'
        })
        reject(new Error(payload.message || 'Upload failed'))
      },
      fail: reject
    })
  })
}

export default {
  get,
  post,
  put,
  del,
  delete: del,
  upload
}

export const setToken = (token) => {
  uni.setStorageSync(tokenKey, token)
}

export const getToken = () => {
  return uni.getStorageSync(tokenKey)
}

export const removeToken = () => {
  uni.removeStorageSync(tokenKey)
}

export const toggleMock = (enabled) => {
  if (process.env.NODE_ENV === 'development') {
    useMock = enabled
    console.log(`mock mode: ${enabled ? 'on' : 'off'}`)
  }
}
