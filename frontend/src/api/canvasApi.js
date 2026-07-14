import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || ''

function getAuthHeader(token) {
  return { Authorization: `Bearer ${token}` }
}

export const canvasApi = {
  getGeneral: (token) =>
    axios.get(`${BASE_URL}/api/canvases/general`, { headers: getAuthHeader(token) }),

  getById: (id, token) =>
    axios.get(`${BASE_URL}/api/canvases/${id}`, { headers: getAuthHeader(token) }),

  getByOwner: (ownerId, token) =>
    axios.get(`${BASE_URL}/api/canvases`, {
      params: { ownerId },
      headers: getAuthHeader(token),
    }),

  create: (name, width, height, ownerId, token) =>
    axios.post(`${BASE_URL}/api/canvases`, { name, width, height, ownerId }, {
      headers: getAuthHeader(token),
    }),

  delete: (id, token) =>
    axios.delete(`${BASE_URL}/api/canvases/${id}`, { headers: getAuthHeader(token) }),

  join: (userId, code, token) =>
    axios.post(`${BASE_URL}/api/canvases/join`, { userId, code }, {
      headers: getAuthHeader(token),
    }),

  createInvitation: (canvasId, requesterId, targetUserId, token) =>
    axios.post(`${BASE_URL}/api/canvases/${canvasId}/invitations`, { requesterId, targetUserId }, {
      headers: getAuthHeader(token),
    }),

  listInvitations: (canvasId, requesterId, token) =>
    axios.get(`${BASE_URL}/api/canvases/${canvasId}/invitations`, {
      params: { requesterId },
      headers: getAuthHeader(token),
    }),

  getMembers: (canvasId, requesterId, token) =>
    axios.get(`${BASE_URL}/api/canvases/${canvasId}/members`, {
      params: { requesterId },
      headers: getAuthHeader(token),
    }),

  removeMember: (canvasId, userId, requesterId, token) =>
    axios.delete(`${BASE_URL}/api/canvases/${canvasId}/members/${userId}`, {
      params: { requesterId },
      headers: getAuthHeader(token),
    }),

  getShared: (userId, token) =>
    axios.get(`${BASE_URL}/api/canvases/shared`, {
      params: { userId },
      headers: getAuthHeader(token),
    }),

  leaveCanvas: (canvasId, userId, token) =>
    axios.post(`${BASE_URL}/api/canvases/${canvasId}/leave`, { userId }, {
      headers: getAuthHeader(token),
    }),

  cancelInvitation: (canvasId, invitationId, requesterId, token) =>
    axios.delete(`${BASE_URL}/api/canvases/${canvasId}/invitations/${invitationId}`, {
      params: { requesterId },
      headers: getAuthHeader(token),
    }),

  respondToInvitation: (canvasId, invitationId, userId, accept, token) =>
    axios.post(`${BASE_URL}/api/canvases/${canvasId}/invitations/${invitationId}/respond`, { userId, accept }, {
      headers: getAuthHeader(token),
    }),

  listMyInvitations: (userId, token) =>
    axios.get(`${BASE_URL}/api/canvases/invitations/mine`, {
      params: { userId },
      headers: getAuthHeader(token),
    }),
}
