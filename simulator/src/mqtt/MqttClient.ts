import mqtt, { type MqttClient as IMqttClient } from "mqtt";

export class SimulatorMqttClient {
  private client: IMqttClient | null = null;
  private readonly topicHandlers = new Map<string, (payload: Buffer) => void>();

  connect(brokerUrl: string): Promise<void> {
    return new Promise((resolve, reject) => {
      this.disconnect();
      const c = mqtt.connect(brokerUrl, {
        clientId: `simulator-${Date.now()}`,
        clean: true,
        reconnectPeriod: 4000,
      });
      this.client = c;
      const ok = () => {
        c.off("error", fail);
        resolve();
      };
      const fail = (err: Error) => {
        c.off("connect", ok);
        reject(err);
      };
      c.once("connect", ok);
      c.once("error", fail);
      c.on("message", (topic, payload) => {
        const h = this.topicHandlers.get(topic);
        if (h) h(payload);
      });
    });
  }

  publish(topic: string, payload: object): void {
    this.client?.publish(topic, JSON.stringify(payload), { qos: 1 });
  }

  subscribe(topic: string, onMessage: (topic: string, payload: string) => void): void {
    this.topicHandlers.set(topic, (buf) => onMessage(topic, buf.toString("utf8")));
    this.client?.subscribe(topic, { qos: 1 }, (err) => {
      if (err) console.error("Subscribe error", topic, err);
    });
  }

  disconnect(): void {
    this.topicHandlers.clear();
    if (this.client) {
      this.client.removeAllListeners();
      this.client.end(true);
      this.client = null;
    }
  }

  get isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}
