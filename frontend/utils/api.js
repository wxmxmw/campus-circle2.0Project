const DEFAULT_BACKEND_ORIGIN = 'http://localhost:8080'
const DEV_BACKEND_ORIGIN_KEY = 'devBackendOrigin'

const normalizeOrigin = (origin) => {
  if (!origin || typeof origin !== 'string') {
    return DEFAULT_BACKEND_ORIGIN
  }
  return origin.replace(/\/+$/, '')
}

const isH5 = typeof window !== 'undefined' && typeof window.location !== 'undefined'

const getDeploymentBasePath = () => {
  if (!isH5) {
    return ''
  }

  const segments = window.location.pathname.split('/').filter(Boolean)
  if (segments.length === 0) {
    return ''
  }

  // H5 is deployed under /ccy/, /cy/, /lqy/, or /wxm/.
  return `/${segments[0]}`
}

const getProductionBackendOrigin = () => {
  if (!isH5) {
    return DEFAULT_BACKEND_ORIGIN
  }

  return `${window.location.origin}${getDeploymentBasePath()}`
}

export const getBackendOrigin = () => {
  const customOrigin = uni.getStorageSync(DEV_BACKEND_ORIGIN_KEY)
  if (customOrigin) {
    return normalizeOrigin(customOrigin)
  }
  if (process.env.NODE_ENV === 'production') {
    return normalizeOrigin(getProductionBackendOrigin())
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
