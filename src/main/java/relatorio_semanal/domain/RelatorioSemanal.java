package relatorio_semanal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Resultado do relatório. inicio e fim estão em UTC; os dias de quantidadePorDia estão no horário de Brasília.
 */
public record RelatorioSemanal(
        LocalDateTime inicio,
        LocalDateTime fim,
        int totalAvaliacoes,
        double mediaNotas,
        Map<LocalDate, Long> quantidadePorDia,
        Map<String, Long> quantidadePorUrgencia,
        List<Avaliacao> avaliacoes) {
}
