import axios from 'axios'
import { message } from 'ant-design-vue'

const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    const msg = err?.response?.data?.message || err?.message || '请求失败'
    message.error(msg)
    return Promise.reject(err)
  },
)

export default http
