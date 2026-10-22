import { reactive } from 'vue'

const savedUser = (() => {
  try { return JSON.parse(localStorage.getItem('fs_user') || 'null') } catch { return null }
})()

export const auth = reactive({
  token: localStorage.getItem('fs_token') || '',
  user: savedUser
})

export function setAuth(data) {
  auth.token = data.token
  auth.user = {
    username: data.username,
    name: data.name,
    role: data.role,
    roleLabel: data.roleLabel
  }
  localStorage.setItem('fs_token', data.token)
  localStorage.setItem('fs_user', JSON.stringify(auth.user))
}

export function clearAuth() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem('fs_token')
  localStorage.removeItem('fs_user')
}

export function hasRole(...roles) {
  return !!auth.user && (auth.user.role === 'admin' || roles.includes(auth.user.role))
}
