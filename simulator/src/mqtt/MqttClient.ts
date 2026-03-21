import mqtt, { type MqttClient as IMqttClient } from "mqtt";

export type MqttAuthOptions = {
  username?: string;
  password?: string;
};

export class SimulatorMqttClient {
  private client: IMqttClient | null = null;
  private readonly topicHandlers = new Map<string, (payload: Buffer) => void>();

  /**
   * RabbitMQ MQTT / Web MQTT expects the same user as AMQP (e.g. admin + RABBITMQ_DEFAULT_PASS).
   */
  connect(brokerUrl: string, auth?: MqttAuthOptions): Promise<void> {
    return new Promise((resolve, reject) => {
      this.disconnect();
      const opts: Parameters<typeof mqtt.connect>[1] = {
        clientId: `simulator-${Date.now()}`,
        clean: true,
        reconnectPeriod: 4000,
      };
      const u = auth?.username?.trim();
      const p = auth?.password ?? "";
      if (u) {
        opts.username = u;
        opts.password = p;
      }
      const c = mqtt.connect(brokerUrl, opts);
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
