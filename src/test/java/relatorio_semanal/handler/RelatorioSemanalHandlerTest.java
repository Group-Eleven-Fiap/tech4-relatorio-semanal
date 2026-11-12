package relatorio_semanal.handler;

import org.junit.jupiter.api.Test;
import relatorio_semanal.domain.Avaliacao;
import relatorio_semanal.domain.RelatorioSemanal;
import relatorio_semanal.service.RelatorioService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelatorioSemanalHandlerTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T11:00:00Z"), ZoneOffset.UTC);

    private static final List<Avaliacao> AVALIACOES = List.of(
            new Avaliacao("1", "Aula sem áudio", 2, "CRITICA", LocalDateTime.parse("2026-11-08T14:32:01")),
            new Avaliacao("2", "Ótima didática", 9, "BAIXA", LocalDateTime.parse("2026-11-09T10:00:00")));

    // Mesmo formato do evento enviado pelo agendador do EventBridge
    private static final Map<String, Object> EVENTO_AGENDADO = Map.of(
            "source", "aws.scheduler",
            "detail-type", "Scheduled Event",
            "time", "2026-11-11T11:00:00Z");

    @Test
    void geraEEnviaORelatorioQuandoOAgendamentoDispara() {
        List<RelatorioSemanal> enviados = new ArrayList<>();
        RelatorioSemanalHandler handler = new RelatorioSemanalHandler(
                new RelatorioService(logger -> AVALIACOES, enviados::add, RELOGIO));

        String resposta = handler.handleRequest(EVENTO_AGENDADO, new TestContext());

        assertEquals("Relatório enviado com 2 avaliações.", resposta);
        assertEquals(1, enviados.size());
        assertEquals(5.5, enviados.get(0).mediaNotas());
    }

    @Test
    void terminaComErroQuandoOEnvioFalha() {
        RelatorioSemanalHandler handler = new RelatorioSemanalHandler(
                new RelatorioService(logger -> AVALIACOES, relatorio -> {
                    throw new IllegalStateException("SES indisponível");
                }, RELOGIO));

        assertThrows(IllegalStateException.class, () -> handler.handleRequest(EVENTO_AGENDADO, new TestContext()));
    }
}
