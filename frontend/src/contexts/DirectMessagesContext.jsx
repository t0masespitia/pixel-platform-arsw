import { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuth } from '../auth/AuthContext.jsx'
import { authApi } from '../api/authApi.js'
import { messagesApi } from '../api/messagesApi.js'

const DirectMessagesContext = createContext(null)

function lastReadKey(currentUserId, otherUserId) {
  return `pp_dm_lastread_${currentUserId}_${otherUserId}`
}

function getLastRead(currentUserId, otherUserId) {
  return localStorage.getItem(lastReadKey(currentUserId, otherUserId))
}

function setLastRead(currentUserId, otherUserId, isoTimestamp) {
  if (!isoTimestamp) return
  localStorage.setItem(lastReadKey(currentUserId, otherUserId), isoTimestamp)
}

function latestSentAt(messages) {
  if (!messages || messages.length === 0) return null
  return messages.reduce((latest, m) => (!latest || new Date(m.sentAt) > new Date(latest) ? m.sentAt : latest), null)
}

export function DirectMessagesProvider({ children }) {
  const { token, userId, isAuthenticated } = useAuth()
  const [conversations, setConversations] = useState({})
  const [unreadCounts, setUnreadCounts] = useState({})
  const [connectionStatus, setConnectionStatus] = useState('disconnected')
  const activeConversationRef = useRef(null)

  useEffect(() => {
    if (!isAuthenticated || !token || !userId) return

    const baseUrl = import.meta.env.VITE_CHAT_SERVICE_URL || window.location.origin
    const client = new Client({
      webSocketFactory: () => new SockJS(`${baseUrl}/ws-chat`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        setConnectionStatus('connected')
        client.subscribe(`/topic/dm/${userId}`, (msg) => {
          const dm = JSON.parse(msg.body)
          const fromUserId = dm.fromUserId
          setConversations((prev) => ({
            ...prev,
            [fromUserId]: [...(prev[fromUserId] || []), dm],
          }))
          if (activeConversationRef.current === fromUserId) {
            setLastRead(userId, fromUserId, dm.sentAt)
          } else {
            setUnreadCounts((prev) => ({
              ...prev,
              [fromUserId]: (prev[fromUserId] || 0) + 1,
            }))
          }
        })
      },
      onDisconnect: () => setConnectionStatus('disconnected'),
      onWebSocketClose: () => setConnectionStatus('disconnected'),
      onStompError: () => setConnectionStatus('error'),
    })

    setConnectionStatus('connecting')
    client.activate()
    return () => { client.deactivate() }
  }, [isAuthenticated, token, userId])

  useEffect(() => {
    if (!isAuthenticated || !token || !userId) return
    let cancelled = false

    authApi.getAllUsers(token)
      .then((res) => {
        const others = res.data.filter((u) => u.userId !== userId)
        return Promise.all(
          others.map((u) =>
            messagesApi.getConversation(token, u.userId)
              .then((r) => ({ otherUserId: u.userId, messages: r.data }))
              .catch(() => ({ otherUserId: u.userId, messages: null }))
          )
        )
      })
      .then((results) => {
        if (cancelled) return
        setConversations((prev) => {
          const next = { ...prev }
          results.forEach(({ otherUserId, messages }) => {
            if (messages) next[otherUserId] = messages
          })
          return next
        })
        setUnreadCounts((prev) => {
          const next = { ...prev }
          results.forEach(({ otherUserId, messages }) => {
            if (!messages) return
            const lastRead = getLastRead(userId, otherUserId)
            const count = messages.filter((m) =>
              m.fromUserId === otherUserId && (!lastRead || new Date(m.sentAt) > new Date(lastRead))
            ).length
            next[otherUserId] = count
          })
          return next
        })
      })
      .catch(() => {})

    return () => { cancelled = true }
  }, [isAuthenticated, token, userId])

  const loadConversation = useCallback(async (otherUserId) => {
    activeConversationRef.current = otherUserId
    setUnreadCounts((prev) => ({ ...prev, [otherUserId]: 0 }))
    const res = await messagesApi.getConversation(token, otherUserId)
    setConversations((prev) => ({ ...prev, [otherUserId]: res.data }))
    setLastRead(userId, otherUserId, latestSentAt(res.data) || new Date().toISOString())
  }, [token, userId])

  const clearActiveConversation = useCallback(() => {
    activeConversationRef.current = null
  }, [])

  const sendMessage = useCallback(async (toUserId, content) => {
    const res = await messagesApi.send(token, toUserId, content)
    setConversations((prev) => ({
      ...prev,
      [toUserId]: [...(prev[toUserId] || []), res.data],
    }))
    return res.data
  }, [token])

  const removeInvitationMessage = useCallback((invitationId) => {
    setConversations((prev) => {
      const next = {}
      Object.entries(prev).forEach(([otherUserId, messages]) => {
        next[otherUserId] = messages.filter((msg) => msg.invitationId !== invitationId)
      })
      return next
    })
  }, [])

  const totalUnread = Object.values(unreadCounts).reduce((sum, n) => sum + n, 0)

  return (
    <DirectMessagesContext.Provider value={{
      conversations,
      unreadCounts,
      totalUnread,
      connectionStatus,
      loadConversation,
      clearActiveConversation,
      sendMessage,
      removeInvitationMessage,
    }}>
      {children}
    </DirectMessagesContext.Provider>
  )
}

export function useDirectMessages() {
  const ctx = useContext(DirectMessagesContext)
  if (!ctx) throw new Error('useDirectMessages must be used within DirectMessagesProvider')
  return ctx
}
