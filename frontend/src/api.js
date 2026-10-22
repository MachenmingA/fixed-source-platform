import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, clearAuth } from './store'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use((config) => {
  if (auth.token) config.headers['X-Token'] = auth.token
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login?redirect=' + encodeURIComponent(location.pathname + location.search)
      }
    } else {
      const data = error.response?.data
      let message = data?.message || data?.error || error.message || '请求失败'
      if (error.response?.status === 403) message = data?.message || '当前角色无权执行此操作'
      if (error.response?.status === 500) message = '服务端处理失败，请查看后端日志'
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default api
