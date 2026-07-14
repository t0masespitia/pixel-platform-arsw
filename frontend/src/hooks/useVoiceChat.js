import { useState, useEffect, useCallback, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }]

export function useVoiceChat(canvasId, userId, token) {
  const [voiceStatus, setVoiceStatus] = useState('idle')
  const [connectedPeers, setConnectedPeers] = useState([])
  const [isMuted, setIsMuted] = useState(false)
  const [micError, setMicError] = useState(null)

  const localStreamRef = useRef(null)
  const stompClientRef = useRef(null)
  const peerConnectionsRef = useRef({})
  const remoteAudiosRef = useRef({})
  const activePeersRef = useRef(new Set())

  const joinVoice = useCallback(async () => {
    setVoiceStatus('requesting-mic')
    setMicError(null)

    let stream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
    } catch {
      setVoiceStatus('error')
      setMicError('Permiso de micrófono denegado. Habilítalo en la configuración del navegador para hablar.')
      return
    }

    localStreamRef.current = stream
    setVoiceStatus('connecting')

    const baseUrl = import.meta.env.VITE_SIGNALING_SERVICE_URL || window.location.origin

    function createPC(peerId) {
      const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS })
      localStreamRef.current.getTracks().forEach((t) => pc.addTrack(t, localStreamRef.current))

      pc.onicecandidate = (e) => {
        if (e.candidate && stompClientRef.current?.connected) {
          stompClientRef.current.publish({
            destination: `/app/signaling/${canvasId}/ice-candidate`,
            body: JSON.stringify({
              fromUserId: userId,
              toUserId: peerId,
              candidate: JSON.stringify(e.candidate),
            }),
          })
        }
      }

      pc.ontrack = (e) => {
        let audio = remoteAudiosRef.current[peerId]
        if (!audio) {
          audio = document.createElement('audio')
          audio.autoplay = true
          audio.style.position = 'absolute'
          audio.style.left = '-9999px'
          document.body.appendChild(audio)
          remoteAudiosRef.current[peerId] = audio
        }
        audio.srcObject = e.streams[0]
        audio.play().catch((err) => {
          console.error(`No se pudo reproducir el audio remoto de ${peerId}:`, err)
        })
      }

      peerConnectionsRef.current[peerId] = pc
      return pc
    }

    function closePC(peerId) {
      const pc = peerConnectionsRef.current[peerId]
      if (pc) { pc.close(); delete peerConnectionsRef.current[peerId] }
      const audio = remoteAudiosRef.current[peerId]
      if (audio) { audio.remove(); delete remoteAudiosRef.current[peerId] }
      activePeersRef.current.delete(peerId)
      setConnectedPeers(Array.from(activePeersRef.current))
    }

    async function handleEvent(data) {
      const client = stompClientRef.current
      switch (data.type) {
        case 'ROOM_JOINED':
          for (const peerId of (data.existingPeers || [])) {
            activePeersRef.current.add(peerId)
            const pc = createPC(peerId)
            const offer = await pc.createOffer()
            await pc.setLocalDescription(offer)
            if (client?.connected) {
              client.publish({
                destination: `/app/signaling/${canvasId}/offer`,
                body: JSON.stringify({ fromUserId: userId, toUserId: peerId, sdp: offer.sdp }),
              })
            }
          }
          setConnectedPeers(Array.from(activePeersRef.current))
          break
        case 'PEER_JOINED':
          activePeersRef.current.add(data.userId)
          setConnectedPeers(Array.from(activePeersRef.current))
          break
        case 'PEER_LEFT':
          closePC(data.userId)
          break
        case 'SDP_OFFER': {
          const pc = createPC(data.fromUserId)
          activePeersRef.current.add(data.fromUserId)
          setConnectedPeers(Array.from(activePeersRef.current))
          await pc.setRemoteDescription({ type: 'offer', sdp: data.sdp })
          const answer = await pc.createAnswer()
          await pc.setLocalDescription(answer)
          if (client?.connected) {
            client.publish({
              destination: `/app/signaling/${canvasId}/answer`,
              body: JSON.stringify({ fromUserId: userId, toUserId: data.fromUserId, sdp: answer.sdp }),
            })
          }
          break
        }
        case 'SDP_ANSWER': {
          const pc = peerConnectionsRef.current[data.fromUserId]
          if (pc) await pc.setRemoteDescription({ type: 'answer', sdp: data.sdp })
          break
        }
        case 'ICE_CANDIDATE': {
          const pc = peerConnectionsRef.current[data.fromUserId]
          if (pc) {
            try { await pc.addIceCandidate(JSON.parse(data.candidate)) } catch (_) {}
          }
          break
        }
      }
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(`${baseUrl}/ws-signaling`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        client.subscribe(`/topic/signaling/${canvasId}/${userId}`, (msg) => {
          handleEvent(JSON.parse(msg.body))
        })
        client.publish({
          destination: `/app/signaling/${canvasId}/join`,
          body: JSON.stringify({ userId }),
        })
        setVoiceStatus('connected')
      },
      onDisconnect: () => setVoiceStatus((s) => (s !== 'idle' ? 'disconnected' : s)),
      onStompError: () => setVoiceStatus('error'),
    })

    stompClientRef.current = client
    client.activate()
  }, [canvasId, userId, token])

  const leaveVoice = useCallback(() => {
    const client = stompClientRef.current
    if (client) {
      try {
        if (client.connected) {
          client.publish({
            destination: `/app/signaling/${canvasId}/leave`,
            body: JSON.stringify({ userId }),
          })
        }
        client.deactivate()
      } catch (_) {}
    }
    Object.values(peerConnectionsRef.current).forEach((pc) => { try { pc.close() } catch (_) {} })
    Object.values(remoteAudiosRef.current).forEach((a) => { try { a.remove() } catch (_) {} })
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((t) => t.stop())
      localStreamRef.current = null
    }
    peerConnectionsRef.current = {}
    remoteAudiosRef.current = {}
    activePeersRef.current.clear()
    stompClientRef.current = null
    setConnectedPeers([])
    setIsMuted(false)
    setVoiceStatus('idle')
  }, [canvasId, userId])

  const toggleMute = useCallback(() => {
    if (!localStreamRef.current) return
    const tracks = localStreamRef.current.getAudioTracks()
    const newEnabled = !tracks[0]?.enabled
    tracks.forEach((t) => { t.enabled = newEnabled })
    setIsMuted(!newEnabled)
  }, [])

  useEffect(() => {
    return () => {
      const client = stompClientRef.current
      if (client) {
        try {
          if (client.connected) {
            client.publish({
              destination: `/app/signaling/${canvasId}/leave`,
              body: JSON.stringify({ userId }),
            })
          }
          client.deactivate()
        } catch (_) {}
      }
      Object.values(peerConnectionsRef.current).forEach((pc) => { try { pc.close() } catch (_) {} })
      Object.values(remoteAudiosRef.current).forEach((a) => { try { a.remove() } catch (_) {} })
      if (localStreamRef.current) {
        localStreamRef.current.getTracks().forEach((t) => { try { t.stop() } catch (_) {} })
      }
      peerConnectionsRef.current = {}
      remoteAudiosRef.current = {}
    }
  }, [canvasId, userId])

  return { voiceStatus, connectedPeers, isMuted, micError, joinVoice, leaveVoice, toggleMute }
}
