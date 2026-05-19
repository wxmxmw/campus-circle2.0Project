const DEFAULT_BACKEND_ORIGIN = 'http://localhost:8080'
const DEV_BACKEND_ORIGIN_KEY = 'devBackendOrigin'

const normalizeOrigin = (origin) => {
  if (!origin || typeof origin !== 'string') {
    return DEFAULT_BACKEND_ORIGIN
  }
  return origin.replace(/\/+$/, '')
}

export const getBackendOrigin = () => {
  const customOrigin = uni.getStorageSync(DEV_BACKEND_ORIGIN_KEY)
  if (customOrigin) {
    return normalizeOrigin(customOrigin)
  }
  return DEFAULT_BACKEND_ORIGIN
}

export const getApiBaseUrl = () => `${getBackendOrigin()}/api`

export const toAbsoluteUrl = (url, fallback = '/static/logo.png') => {
  if (!url) {
    return fallback
  }
  if (/^(https?:|data:|blob:|file:|wxfile:|_doc\/|\/?static\/|(\.\.\/)+static\/|\.\/static\/)/i.test(url)) {
    return url
  }
  return `${getBackendOrigin()}${url.startsWith('/') ? url : `/${url}`}`
}

export const setDevBackendOrigin = (origin) => {
  const normalized = normalizeOrigin(origin)
  uni.setStorageSync(DEV_BACKEND_ORIGIN_KEY, normalized)
  return normalized
}

export const clearDevBackendOrigin = () => {
  uni.removeStorageSync(DEV_BACKEND_ORIGIN_KEY)
}
