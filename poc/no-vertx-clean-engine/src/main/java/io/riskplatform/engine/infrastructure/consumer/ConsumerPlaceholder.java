package io.riskplatform.engine.infrastructure.consumer;

/**
 * Placeholder — esta carpeta contendrá consumers de mensajería (inbound adapters async).
 *
 * <p>Convención de layout enterprise Go: los consumers viven en {@code internal/infrastructure/consumers/} y
 * exponen listeners de Kafka/Redpanda (equivalente a {@code @KafkaListener} en Spring).
 * En esta PoC el "consumer" es el outbox relay en memoria ({@code AsyncOutboxRelay}). Cuando
 * reemplazemos {@code InMemoryOutboxRepository} por un producer real de Redpanda, el consumer
 * de eventos de decisión iría aquí, no en {@code infrastructure/repository/event/}.
 *
 * <p>Ejemplo futuro: {@code RiskDecisionEventConsumer} — escucha el topic
 * {@code risk.decision.created} y dispara side-effects secundarios (notificaciones, auditoría).
 */
final class ConsumerPlaceholder {
    private ConsumerPlaceholder() {}
}
