import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import Vuex from 'vuex'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
  ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
// 1. 必须在这里（Vue3 块内部）引入 Pinia
import * as Pinia from 'pinia' 

export function createApp() {
  const app = createSSRApp(App)
  
  // 2. 创建 Pinia 实例并挂载
  // 因为上面正确引入了 Pinia，这里才能调用 createPinia
  const store = Pinia.createPinia()
  app.use(store)
  
  return {
    app,
    Pinia // 导出
  }
}
// #endif
