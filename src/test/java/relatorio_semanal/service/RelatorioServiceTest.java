package relatorio_semanal.service;

import org.junit.jupiter.api.Test;
import relatorio_semanal.domain.Avaliacao;
import relatorio_semanal.domain.RelatorioSemanal;
import relatorio_semanal.handler.TestContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatorioServiceTest {

    // 11/11/2026 às 08:00 em Brasília
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T11:00:00Z"), ZoneOffset.UTC);

    private final List<RelatorioSemanal> enviados = new ArrayList<>();

    private RelatorioSemanal gerar(Avaliacao... avaliacoes) {
        RelatorioService service = new RelatorioService(logger -> List.of(avaliacoes), enviados::add, RELOGIO);
        return service.gerarEEnviar(new TestContext().getLogger());
    }

    private static Avaliacao avaliacao(String id, int nota, String urgencia, String dataEnvio) {
        return new Avaliacao(id, "Descrição " + id, nota, urgencia, LocalDateTime.parse(dataEnvio));
    }

    @Test
    void calculaMediaEQuantidadesDasAvaliacoesDoPeriodo() {
        RelatorioSemanal relatorio = gerar(
                avaliacao("b", 6, "MEDIA", "2026-11-05T15:00:00"),
                avaliacao("a", 2, "CRITICA", "2026-11-05T14:00:00"),
                avaliacao("c", 10, "BAIXA", "2026-11-08T12:00:00"));

        assertEquals(LocalDateTime.parse("2026-11-04T11:00:00"), relatorio.inicio());
        assertEquals(LocalDateTime.parse("2026-11-11T11:00:00"), relatorio.fim());
        assertEquals(3, relatorio.totalAvaliacoes());
        assertEquals(6.0, relatorio.mediaNotas());
        assertEquals(Map.of("CRITICA", 1L, "MEDIA", 1L, "BAIXA", 1L), relatorio.quantidadePorUrgencia());
        assertEquals(2L, relatorio.quantidadePorDia().get(LocalDate.parse("2026-11-05")));
        assertEquals(1L, relatorio.quantidadePorDia().get(LocalDate.parse("2026-11-08")));
        // Lista em ordem de data de envio
        assertEquals(List.of("a", "b", "c"), relatorio.avaliacoes().stream().map(Avaliacao::id).toList());
        assertEquals(List.of(relatorio), enviados);
    }

    @Test
    void ignoraAvaliacoesForaDosUltimosSeteDias() {
        RelatorioSemanal relatorio = gerar(
                avaliacao("antiga", 0, "CRITICA", "2026-11-04T10:59:59"),
                avaliacao("primeira", 8, "BAIXA", "2026-11-04T11:00:00"),
                // Exatamente no fim do período: fica para o relatório da semana seguinte
                avaliacao("proxima-semana", 0, "CRITICA", "2026-11-11T11:00:00"));

        assertEquals(List.of("primeira"), relatorio.avaliacoes().stream().map(Avaliacao::id).toList());
        assertEquals(8.0, relatorio.mediaNotas());
    }

    @Test
    void contaODiaNoHorarioDeBrasilia() {
        // 02:00 em UTC do dia 10 ainda é 23:00 do dia 9 em Brasília
        RelatorioSemanal relatorio = gerar(avaliacao("a", 7, "BAIXA", "2026-11-10T02:00:00"));

        assertEquals(1L, relatorio.quantidadePorDia().get(LocalDate.parse("2026-11-09")));
        assertEquals(0L, relatorio.quantidadePorDia().get(LocalDate.parse("2026-11-10")));
    }

    @Test
    void enviaRelatorioZeradoQuandoNaoHaAvaliacoes() {
        RelatorioSemanal relatorio = gerar();

        assertEquals(0, relatorio.totalAvaliacoes());
        assertEquals(0.0, relatorio.mediaNotas());
        assertEquals(Map.of("CRITICA", 0L, "MEDIA", 0L, "BAIXA", 0L), relatorio.quantidadePorUrgencia());
        // De 04/11 a 11/11, todos com zero
        assertEquals(8, relatorio.quantidadePorDia().size());
        assertTrue(relatorio.quantidadePorDia().values().stream().allMatch(quantidade -> quantidade == 0));
        assertEquals(1, enviados.size());
    }
}
