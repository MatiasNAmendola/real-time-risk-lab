package com.naranjax.interview.risk.application.mapper;

/**
 * Placeholder — esta carpeta contiene mappers entre DTOs de aplicación y entidades de dominio.
 *
 * <p>Convención de layout enterprise Go: los mappers viven en {@code internal/application/mappers/} y se
 * encargan de traducir entre la capa de infraestructura (requests/responses HTTP, mensajes Kafka)
 * y las entidades del dominio. En Java, seguimos la misma separación: los adaptadores no conocen
 * el dominio directamente; pasan por un mapper explícito.
 *
 * <p>Ejemplo: {@code RiskDecisionMapper} traduce {@code EvaluateRiskRequestDTO →
 * TransactionRiskRequest} y {@code RiskDecision → RiskDecisionResponseDTO}.
 */
final class MapperPlaceholder {
    private MapperPlaceholder() {}
}
