import { useState, useEffect, useCallback, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const MAX_MESSAGES = 200

export function useChatSocket(canvasId, userId, token) {
  const [messages, setMessages] = useState([])
  const [connectionStatus, setConnectionStatus] = useState('connecting')
  const clientRef = useRef(null)

  useEffect(() => {
    if (!canvasId) return
    const baseUrl = import.meta.env.VITE_CHAT_SERVICE_URL || window.location.origin

    const client = new Client({
      webSocketFactory: () => new SockJS(`${baseUrl}/ws-chat`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        setConnectionStatus('connected')
        client.subscribe(`/topic/chat/${canvasId}`, (msg) => {
          const data = JSON.parse(msg.body)
          setMessages((prev) => {
            const next = [...prev, data]
            return next.length > MAX_MESSAGES ? next.slice(next.length - MAX_MESSAGES) : next
          })
        })
      },
      onDisconnect: () => setConnectionStatus('disconnected'),
      onWebSocketClose: () => setConnectionStatus('disconnected'),
      onStompError: () => setConnectionStatus('error'),
    })

    clientRef.current = client
    client.activate()
    return () => { client.deactivate() }
  }, [canvasId, token])

  const sendMessage = useCallback(
    (text) => {
      const client = clientRef.current
      if (!client?.connected || !text.trim()) return
      client.publish({
        destination: `/app/chat/${canvasId}/send`,
        body: JSON.stringify({ userId, message: text }),
      })
    },
    [canvasId, userId],
  )

  return { messages, connectionStatus, sendMessage }
}
