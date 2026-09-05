import axios from 'axios'

export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api', timeout: 15000 })
http.interceptors.request.use(config => {
  const token = localStorage.getItem('ai_kh_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
http.interceptors.response.use(response => response.data?.data ?? response.data, error => {
  if (error.response?.status === 401) {
    localStorage.removeItem('ai_kh_token'); localStorage.removeItem('ai_kh_user'); window.location.href = '/login'
  }
  return Promise.reject(error)
})

