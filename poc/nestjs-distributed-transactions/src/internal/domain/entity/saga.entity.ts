export type SagaStatus = 'PENDING' | 'COMPLETED' | 'COMPENSATING' | 'COMPENSATED' | 'FAILED';
export type SagaStepStatus = 'PENDING' | 'DONE' | 'COMPENSATED' | 'FAILED' | 'SKIPPED';

export type SagaStepName = 'reserve-inventory' | 'post-ledger-transfer' | 'notify-downstream';

export interface SagaStep {
  name: SagaStepName;
  status: SagaStepStatus;
  reason?: string;
  startedAt?: string;
  finishedAt?: string;
}

export class TransactionSaga {
  readonly steps: SagaStep[] = [
    { name: 'reserve-inventory', status: 'PENDING' },
    { name: 'post-ledger-transfer', status: 'PENDING' },
    { name: 'notify-downstream', status: 'PENDING' },
  ];

  constructor(
    public readonly sagaId: string,
    public readonly transactionId: string,
    public readonly correlationId: string,
    public status: SagaStatus = 'PENDING',
    public readonly createdAt: string = new Date().toISOString(),
    public updatedAt: string = new Date().toISOString(),
  ) {}

  markStep(name: SagaStepName, status: SagaStepStatus, reason?: string): void {
    const step = this.step(name);
    step.status = status;
    step.reason = reason;
    step.startedAt ??= new Date().toISOString();
    step.finishedAt = new Date().toISOString();
    this.updatedAt = step.finishedAt;
  }

  stepStatus(name: SagaStepName): SagaStepStatus {
    return this.step(name).status;
  }

  isStepDone(name: SagaStepName): boolean {
    return this.stepStatus(name) === 'DONE';
  }

  markPendingAsSkipped(reason: string): void {
    for (const step of this.steps) {
      if (step.status === 'PENDING') {
        this.markStep(step.name, 'SKIPPED', reason);
      }
    }
  }

  complete(): void {
    this.status = 'COMPLETED';
    this.updatedAt = new Date().toISOString();
  }

  compensate(): void {
    this.status = 'COMPENSATING';
    this.updatedAt = new Date().toISOString();
  }

  compensated(): void {
    this.status = 'COMPENSATED';
    this.updatedAt = new Date().toISOString();
  }

  fail(reason: string): void {
    this.status = 'FAILED';
    this.markPendingAsSkipped(reason);
    this.updatedAt = new Date().toISOString();
  }

  private step(name: SagaStepName): SagaStep {
    const step = this.steps.find((candidate) => candidate.name === name);
    if (!step) throw new Error(`unknown saga step ${name}`);
    return step;
  }
}
