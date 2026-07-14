import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080'

export const aiApi = {
  generateTemplate: (token, canvasId, requesterId, file, mode = 'COLOR') => {
    const formData = new FormData()
    formData.append('requesterId', requesterId)
    formData.append('file', file)
    formData.append('mode', mode)
    return axios.post(`${BASE_URL}/api/ai/canvases/${canvasId}/generate-template`, formData, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'multipart/form-data',
      },
    })
  },
}
