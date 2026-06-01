import { Job, Queue, Worker } from 'bullmq';
import { createDomainMessage, idempotencyKeyFor } from '../../application/mapper/domain-message.factory';
import { PublishEdaMessageInput } from '../../application/dto/publish-eda-message.input';
import { DomainMessage } from '../../domain/event/domain-message';
import type { MessageIdempotencyRepository } from '../../domain/repository/message-idempotency.repository';

export interface EdaPublishResult {
  queue: string;
  jobId: string;
  idempotencyKey: string;
  message: DomainMessage;
}

export interface EdaProcessResult {
  jobId: string;
  idempotencyKey: string;
  accepted: boolean;
  reason: string;
  checksum: string;
  domainId: string;
}

export class BullMqEdaService {
  private readonly queueName = process.env.EDA_QUEUE_NAME ?? 'risk-domain-events-hono';
  private readonly connection = {
    host: process.env.VALKEY_HOST ?? 'localhost',
    port: Number(process.env.VALKEY_PORT ?? 6379),
    maxRetriesPerRequest: null,
  };
  private queueInstance?: Queue<DomainMessage>;
  private worker?: Worker<DomainMessage, EdaProcessResult>;

  constructor(private readonly idempotency: MessageIdempotencyRepository) {}

  startWorker(): void {
    if (process.env.EDA_WORKER_ENABLED !== 'true') return;
    this.queue();
    this.worker = new Worker<DomainMessage, EdaProcessResult>(this.queueName, async (job) => this.processMessage(job), {
      connection: this.connection,
    });
  }

  async publish(input: PublishEdaMessageInput, correlationId: string): Promise<EdaPublishResult> {
    const message = createDomainMessage(input, correlationId);
    const idempotencyKey = idempotencyKeyFor(message);
    const queue = this.queue();
    const existing = await queue.getJob(idempotencyKey);
    if (existing) {
      return { queue: this.queueName, jobId: existing.id ?? idempotencyKey, idempotencyKey, message: existing.data };
    }
    const job = await queue.add('domain-event', message, {
      jobId: idempotencyKey,
      removeOnComplete: false,
      removeOnFail: false,
      attempts: 3,
      backoff: { type: 'exponential', delay: 100 },
    });
    return { queue: this.queueName, jobId: job.id ?? idempotencyKey, idempotencyKey, message };
  }

  async processById(jobId: string): Promise<EdaProcessResult> {
    const job = await this.queue().getJob(jobId);
    if (!job) throw new Error(`job not found: ${jobId}`);
    return this.processMessage(job);
  }

  async processMessage(job: Job<DomainMessage>): Promise<EdaProcessResult> {
    const message = job.data;
    const idempotencyKey = idempotencyKeyFor(message);
    const result = await this.idempotency.markFirstSeen({
      idempotencyKey,
      domainId: message.domainId,
      checksum: message.checksum,
      firstSeenAt: new Date().toISOString(),
    });
    return {
      jobId: job.id ?? idempotencyKey,
      idempotencyKey,
      accepted: result.accepted,
      reason: result.reason,
      checksum: message.checksum,
      domainId: message.domainId,
    };
  }

  async close(): Promise<void> {
    await this.worker?.close();
    await this.queueInstance?.close();
  }

  private queue(): Queue<DomainMessage> {
    this.queueInstance ??= new Queue<DomainMessage>(this.queueName, { connection: this.connection });
    return this.queueInstance;
  }
}
