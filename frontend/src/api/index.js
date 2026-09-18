import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const linenApi = {
  list: (params) => http.get('/linens', { params }),
  create: (data) => http.post('/linens', data),
  update: (id, data) => http.put(`/linens/${id}`, data)
}

export const batchApi = {
  list: (params) => http.get('/batches', { params }),
  create: (data) => http.post('/batches', data),
  advance: (id, action, payload) =>
    http.post(`/batches/${id}/advance`, payload || null, { params: { action } })
}

export const shortageApi = {
  list: (params) => http.get('/shortages', { params }),
  replenish: (id) => http.post(`/shortages/${id}/replenish`),
  toLoss: (id, reason) => http.post(`/shortages/${id}/to-loss`, null, { params: { reason } })
}

export const issueApi = {
  list: (params) => http.get('/issues', { params }),
  create: (data) => http.post('/issues', data),
  giveBack: (id, backQty) => http.post(`/issues/${id}/giveback`, null, { params: { backQty } })
}

export const lossApi = {
  list: (params) => http.get('/losses', { params }),
  create: (data) => http.post('/losses', data),
  confirm: (id) => http.post(`/losses/${id}/confirm`)
}

export const sealApi = {
  list: (params) => http.get('/seals', { params }),
  create: (data) => http.post('/seals', data),
  specialWash: (id, data) => http.post(`/seals/${id}/special-wash`, data),
  release: (id, releaser) =>
    http.post(`/seals/${id}/release`, null, { params: { releaser } })
}

export default http
