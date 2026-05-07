import {
  SQSClient,
  SendMessageCommand,
  ReceiveMessageCommand,
  DeleteMessageCommand,
} from '@aws-sdk/client-sqs';
import { ClientOptions, RiskDecision, RiskRequest } from './types';
import { resolveEnv } from './env';

export class QueueChannel {
  private readonly sqs: SQSClient;
  private readonly queueUrl: string;

  constructor(
    private readonly options: ClientOptions,
    sqsOverride?: SQSClient,
  ) {
    this.sqs      = sqsOverride ?? new SQSClient({ region: 'us-east-1' });
    this.queueUrl = resolveEnv(options.environment).sqsQueueUrl;
  }

  async sendDecisionRequest(req: RiskRequest): Promise<void> {
    await this.sqs.send(
      new SendMessageCommand({
        QueueUrl:    this.queueUrl,
        MessageBody: JSON.stringify(req),
      }),
    );
  }

  /**
   * Poll the queue once (long-poll) and invoke the handler for each message.
   * Returns the number of messages processed.
   */
  async receiveDecisions(
    handler: (decision: RiskDecision) => Promise<void>,
  ): Promise<number> {
    const resp = await this.sqs.send(
      new ReceiveMessageCommand({
        QueueUrl:            this.queueUrl,
        MaxNumberOfMessages: 10,
        WaitTimeSeconds:     20,
      }),
    );

    let processed = 0;
    for (const msg of resp.Messages ?? []) {
      try {
        const decision = JSON.parse(msg.Body!) as RiskDecision;
        await handler(decision);
        await this.sqs.send(
          new DeleteMessageCommand({
            QueueUrl:      this.queueUrl,
            ReceiptHandle: msg.ReceiptHandle!,
          }),
        );
        processed++;
      } catch { /* leave in queue for retry / DLQ */ }
    }
    return processed;
  }
}
