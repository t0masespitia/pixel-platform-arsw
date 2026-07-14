import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || ''

export const authApi = {
  login: (email, password) =>
    axios.post(`${BASE_URL}/api/auth/login`, { email, password }),
  register: (firstName, lastName, email, password) =>
    axios.post(`${BASE_URL}/api/auth/register`, { firstName, lastName, email, password }),
  verifyEmail: (email, code) =>
    axios.post(`${BASE_URL}/api/auth/verify-email`, { email, code }),
  resendCode: (email) =>
    axios.post(`${BASE_URL}/api/auth/resend-code`, { email }),
  getAllUsers: (token) =>
    axios.get(`${BASE_URL}/api/auth/users`, {
      headers: { Authorization: `Bearer ${token}` },
    }),
  getDirectory: (token, requesterId, { letter, query, page = 0, size = 8 } = {}) =>
    axios.get(`${BASE_URL}/api/auth/users/directory`, {
      params: {
        requesterId,
        letter: letter || undefined,
        query: query || undefined,
        page,
        size,
      },
      headers: { Authorization: `Bearer ${token}` },
    }),

  getUserProfile: (userId, token) =>
    axios.get(`${BASE_URL}/api/auth/users/${userId}`, {
      headers: { Authorization: `Bearer ${token}` },
    }),

  uploadAvatar: (token, file) => {
    const formData = new FormData()
    formData.append('file', file)
    return axios.post(`${BASE_URL}/api/auth/me/avatar`, formData, {
      headers: { Authorization: `Bearer ${token}` },
    })
  },

  deleteAvatar: (token) =>
    axios.delete(`${BASE_URL}/api/auth/me/avatar`, {
      headers: { Authorization: `Bearer ${token}` },
    }),
}
