package relatorio_semanal.domain;

import java.time.LocalDateTime;

/**
 * Avaliação gravada na tabela Avaliacoes pelo tech4-ingestao-avaliacao.
 * dataEnvio está em UTC, que é o relógio da Lambda de ingestão.
 */
public record Avaliacao(String id, String descricao, int nota, String urgencia, LocalDateTime dataEnvio) {
}
