import { useEffect, useRef, useCallback, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { Alert } from '@/types';

// Must match alert-service WebSocketConfig: registry.addEndpoint("/ws/alerts").withSockJS()
const WS_URL =
  import.meta.env.VITE_WS_URL ||
  (import.meta.env.DEV ? '/ws/alerts' : 'http://localhost:8081/ws/alerts');

interface UseAlertWebSocketOptions {
  onAlert?: (alert: Alert) => void;
  enabled?: boolean;
}

export function useAlertWebSocket({ onAlert, enabled = true }: UseAlertWebSocketOptions = {}) {
  const clientRef = useRef<Client | null>(null);
  const [connected, setConnected] = useState(false);
  const [liveAlerts, setLiveAlerts] = useState<Alert[]>([]);

  const handleAlert = useCallback(
    (alert: Alert) => {
      setLiveAlerts((prev) => [alert, ...prev].slice(0, 50));
      onAlert?.(alert);
    },
    [onAlert],
  );

  useEffect(() => {
    if (!enabled) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/alerts', (message) => {
          try {
            const alert: Alert = JSON.parse(message.body);
            handleAlert(alert);
          } catch {
            // ignore parse errors
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [enabled, handleAlert]);

  return { connected, liveAlerts, clearAlerts: () => setLiveAlerts([]) };
}
