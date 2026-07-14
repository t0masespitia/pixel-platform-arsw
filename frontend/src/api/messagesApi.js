import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080'

export const messagesApi = {
  send: (token, toUserId, content) =>
    axios.post(`${BASE_URL}/api/messages`, { toUserId, content }, {
      headers: { Authorization: `Bearer ${token}` },
    }),
  getConversation: (token, otherUserId) =>
    axios.get(`${BASE_URL}/api/messages/${otherUserId}`, {
      headers: { Authorization: `Bearer ${token}` },
    }),
}
