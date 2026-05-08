package riskclient

import (
	"context"
	"fmt"
	"time"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
)

// Client is the entry point for the Real-Time Risk Lab Go SDK.
//
//	client, err := riskclient.New(ctx, riskclient.Config{
//	    Environment: riskclient.Local,
//	    APIKey:      os.Getenv("RISK_API_KEY"),
//	    Timeout:     280 * time.Millisecond,
//	    Retry:       riskclient.ExponentialBackoff(),
//	})
//	decision, err := client.Sync.Evaluate(ctx, req)
type Client struct {
	Sync     *SyncClient
	Stream   *StreamClient
	Channel  *ChannelClient
	Events   *EventsClient
	Queue    *QueueClient
	Webhooks *WebhooksClient
	Admin    *AdminClient
}

// New creates a Client with the provided configuration.
// A default SQS client is built from the ambient AWS environment.
// To inject a custom SQS client (e.g. in tests), use NewWithSQS.
func New(ctx context.Context, cfg Config) (*Client, error) {
	if cfg.APIKey == "" {
		return nil, fmt.Errorf("riskclient: APIKey is required")
	}
	if cfg.Timeout == 0 {
		cfg.Timeout = 280 * time.Millisecond
	}
	if cfg.Retry.MaxAttempts == 0 {
		cfg.Retry = ExponentialBackoff()
	}

	awsCfg, err := awsconfig.LoadDefaultConfig(ctx)
	if err != nil {
		return nil, fmt.Errorf("riskclient: load AWS config: %w", err)
	}
	sqsClient := sqs.NewFromConfig(awsCfg)

	return newClient(cfg, sqsClient), nil
}

// NewWithSQS is identical to New but accepts an explicit SQS client.
// Useful for tests that inject a mock.
func NewWithSQS(cfg Config, sqsClient SQSClientAPI) *Client {
	if cfg.Timeout == 0 {
		cfg.Timeout = 280 * time.Millisecond
	}
	if cfg.Retry.MaxAttempts == 0 {
		cfg.Retry = ExponentialBackoff()
	}
	return newClient(cfg, sqsClient)
}

func newClient(cfg Config, sqsClient SQSClientAPI) *Client {
	h := newJSONHTTP(cfg)
	return &Client{
		Sync:     newSyncClient(cfg, h),
		Stream:   newStreamClient(cfg, h),
		Channel:  newChannelClient(cfg),
		Events:   newEventsClient(cfg),
		Queue:    newQueueClient(cfg, sqsClient),
		Webhooks: newWebhooksClient(cfg, h),
		Admin:    newAdminClient(cfg, h),
	}
}

// NewWithServerOverride is for testing only. It points all HTTP sub-clients
// at serverURL instead of the environment-derived URL.
func NewWithServerOverride(cfg Config, serverURL string, sqsClient SQSClientAPI) *Client {
	if cfg.Timeout == 0 {
		cfg.Timeout = 280 * time.Millisecond
	}
	if cfg.Retry.MaxAttempts == 0 {
		cfg.Retry = ExponentialBackoff()
	}
	if sqsClient == nil {
		sqsClient = &noopSQS{}
	}

	// Temporarily override the environment map entry for Local.
	// We set Environment=Local then override the coords.
	cfg.Environment = Local
	origCoords := envMap[Local]
	envMap[Local] = envCoords{
		restBaseURL: serverURL,
		kafkaBroker: origCoords.kafkaBroker,
		sqsQueueURL: origCoords.sqsQueueURL,
	}
	client := newClient(cfg, sqsClient)
	envMap[Local] = origCoords // restore
	return client
}

// noopSQS is a no-op SQS client used when no SQS mock is provided.
type noopSQS struct{}

func (n *noopSQS) SendMessage(_ context.Context, _ *sqs.SendMessageInput, _ ...func(*sqs.Options)) (*sqs.SendMessageOutput, error) {
	return &sqs.SendMessageOutput{}, nil
}
func (n *noopSQS) ReceiveMessage(_ context.Context, _ *sqs.ReceiveMessageInput, _ ...func(*sqs.Options)) (*sqs.ReceiveMessageOutput, error) {
	return &sqs.ReceiveMessageOutput{}, nil
}
func (n *noopSQS) DeleteMessage(_ context.Context, _ *sqs.DeleteMessageInput, _ ...func(*sqs.Options)) (*sqs.DeleteMessageOutput, error) {
	return &sqs.DeleteMessageOutput{}, nil
}
