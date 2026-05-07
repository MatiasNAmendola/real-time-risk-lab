import { Kafka, Consumer, Producer, EachMessagePayload } from 'kafkajs';
import { ClientOptions, DecisionEvent } from './types';
import { resolveEnv } from './env';

const DECISIONS_TOPIC     = 'risk-decisions';
const CUSTOM_EVENTS_TOPIC = 'risk-custom-events';

export class EventsChannel {
  private readonly kafka: Kafka;

  constructor(private readonly options: ClientOptions) {
    this.kafka = new Kafka({
      clientId: 'risk-client-ts',
      brokers: [resolveEnv(options.environment).kafkaBroker],
    });
  }

  /**
   * Subscribe to risk-decisions topic and invoke handler for each event.
   * Resolves when the consumer is stopped.
   */
  async consumeDecisions(
    groupId: string,
    handler: (event: DecisionEvent) => Promise<void>,
  ): Promise<void> {
    const consumer: Consumer = this.kafka.consumer({ groupId });
    await consumer.connect();
    await consumer.subscribe({ topic: DECISIONS_TOPIC, fromBeginning: false });

    await consumer.run({
      eachMessage: async ({ message }: EachMessagePayload) => {
        if (!message.value) return;
        try {
          const event = JSON.parse(message.value.toString()) as DecisionEvent;
          await handler(event);
        } catch { /* skip unparseable */ }
      },
    });
  }

  /** Publish a custom event envelope to the risk-custom-events topic. */
  async publishCustomEvent(envelope: Record<string, unknown>): Promise<void> {
    const producer: Producer = this.kafka.producer();
    await producer.connect();
    try {
      await producer.send({
        topic: CUSTOM_EVENTS_TOPIC,
        messages: [{ value: JSON.stringify(envelope) }],
      });
    } finally {
      await producer.disconnect();
    }
  }
}
