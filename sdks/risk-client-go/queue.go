package riskclient

import (
	"context"
	"encoding/json"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
)

// SQSClientAPI is the subset of the AWS SQS client used by QueueClient.
// It is an interface to allow test doubles.
type SQSClientAPI interface {
	SendMessage(ctx context.Context, params *sqs.SendMessageInput, optFns ...func(*sqs.Options)) (*sqs.SendMessageOutput, error)
	ReceiveMessage(ctx context.Context, params *sqs.ReceiveMessageInput, optFns ...func(*sqs.Options)) (*sqs.ReceiveMessageOutput, error)
	DeleteMessage(ctx context.Context, params *sqs.DeleteMessageInput, optFns ...func(*sqs.Options)) (*sqs.DeleteMessageOutput, error)
}

// QueueClient encapsulates SQS operations.
type QueueClient struct {
	sqs      SQSClientAPI
	queueURL string
}

func newQueueClient(cfg Config, sqsClient SQSClientAPI) *QueueClient {
	return &QueueClient{sqs: sqsClient, queueURL: envMap[cfg.Environment].sqsQueueURL}
}

// SendDecisionRequest serializes req and enqueues it.
func (q *QueueClient) SendDecisionRequest(ctx context.Context, req RiskRequest) error {
	b, err := json.Marshal(req)
	if err != nil {
		return &RiskClientError{Message: "marshal request", Cause: err}
	}
	_, err = q.sqs.SendMessage(ctx, &sqs.SendMessageInput{
		QueueUrl:    aws.String(q.queueURL),
		MessageBody: aws.String(string(b)),
	})
	return err
}

// ReceiveDecisions polls once and invokes handler for each message.
// Returns the number of messages processed.
func (q *QueueClient) ReceiveDecisions(
	ctx context.Context,
	handler func(ctx context.Context, d RiskDecision) error,
) (int, error) {
	resp, err := q.sqs.ReceiveMessage(ctx, &sqs.ReceiveMessageInput{
		QueueUrl:            aws.String(q.queueURL),
		MaxNumberOfMessages: 10,
		WaitTimeSeconds:     20,
	})
	if err != nil {
		return 0, &RiskClientError{Message: "SQS receive", Cause: err}
	}

	processed := 0
	for _, msg := range resp.Messages {
		var dec RiskDecision
		if jsonErr := json.Unmarshal([]byte(aws.ToString(msg.Body)), &dec); jsonErr != nil {
			continue
		}
		if handlerErr := handler(ctx, dec); handlerErr != nil {
			continue // leave in queue for retry / DLQ
		}
		_, _ = q.sqs.DeleteMessage(ctx, &sqs.DeleteMessageInput{
			QueueUrl:      aws.String(q.queueURL),
			ReceiptHandle: msg.ReceiptHandle,
		})
		processed++
	}
	return processed, nil
}
