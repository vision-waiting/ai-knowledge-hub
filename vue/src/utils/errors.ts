export function errorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  const value = error as { response?: { data?: { message?: string } }; message?: string }
  return value?.response?.data?.message || value?.message || fallback
}
export function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
