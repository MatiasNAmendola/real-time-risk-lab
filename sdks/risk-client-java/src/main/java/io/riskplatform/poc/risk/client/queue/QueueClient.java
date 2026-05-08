package io.riskplatform.rules.client.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riskplatform.rules.client.RiskClientException;
import io.riskplatform.rules.client.config.ClientConfig;
import io.riskplatform.sdks.riskevents.RiskDecision;
import io.riskplatform.sdks.riskevents.RiskRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.function.Consumer;

/**
 * SQS channel — encapsulates queue URL, region, and message serialization.
 */
public final class QueueClient {

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final String queueUrl;

    public QueueClient(ClientConfig config, SqsClient sqs, ObjectMapper mapper) {
        this.sqs      = sqs;
        this.mapper   = mapper;
        this.queueUrl = config.environment().sqsQueueUrl();
    }

    /** Serializes a RiskRequest and sends it to the SQS queue. */
    public void sendDecisionRequest(RiskRequest req) {
        try {
            String body = mapper.writeValueAsString(req);
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
        } catch (Exception e) {
            throw new RiskClientException("Failed to send decision request to SQS", e);
        }
    }

    /**
     * Polls the queue once (long-poll 20 s), deserializes each message, and
     * invokes the handler. Deletes the message after successful handling.
     * Returns the number of messages processed.
     */
    public int receiveDecisions(Consumer<RiskDecision> handler) {
        var response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20)
                .build());

        int processed = 0;
        for (var msg : response.messages()) {
            try {
                RiskDecision decision = mapper.readValue(msg.body(), RiskDecision.class);
                handler.accept(decision);
                sqs.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build());
                processed++;
            } catch (Exception e) {
                // leave message in queue for retry / DLQ
            }
        }
        return processed;
    }
}
