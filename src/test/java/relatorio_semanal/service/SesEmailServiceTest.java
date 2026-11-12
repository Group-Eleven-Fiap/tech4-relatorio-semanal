package relatorio_semanal.service;

import org.junit.jupiter.api.Test;
import relatorio_semanal.domain.Avaliacao;
import relatorio_semanal.domain.RelatorioSemanal;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SesEmailServiceTest {

    private static final LocalDateTime INICIO = LocalDateTime.parse("2026-11-04T11:00:00");
    private static final LocalDateTime FIM = LocalDateTime.parse("2026-11-11T11:00:00");

    private SendEmailRequest requisicao;

    private SesEmailService servico(Supplier<SendEmailResponse> resposta) {
        SesClient cliente = new SesClient() {
            @Override
            public SendEmailResponse sendEmail(SendEmailRequest request) {
                requisicao = request;
                return resposta.get();
            }

            @Override public String serviceName() { return "ses"; }
            @Override public void close() { }
        };
        return new SesEmailService(cliente, new EmailTemplateService(), "remetente@fiap.com", "admin@fiap.com");
    }

    private static RelatorioSemanal relatorioComDuasAvaliacoes() {
        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        porDia.put(LocalDate.parse("2026-11-08"), 2L);
        porDia.put(LocalDate.parse("2026-11-09"), 0L);

        Map<String, Long> porUrgencia = new LinkedHashMap<>();
        porUrgencia.put("CRITICA", 1L);
        porUrgencia.put("MEDIA", 0L);
        porUrgencia.put("BAIXA", 1L);

        return new RelatorioSemanal(INICIO, FIM, 2, 5.5, porDia, porUrgencia, List.of(
                new Avaliacao("1", "Aula sem áudio", 2, "CRITICA", LocalDateTime.parse("2026-11-08T14:32:01.169")),
                new Avaliacao("2", "Ótima didática", 9, "BAIXA", LocalDateTime.parse("2026-11-08T18:05:00"))));
    }

    @Test
    void enviaParaOAdministradorComOsDadosExigidos() {
        servico(() -> SendEmailResponse.builder().messageId("id").build()).enviar(relatorioComDuasAvaliacoes());

        assertEquals("remetente@fiap.com", requisicao.source());
        assertEquals(List.of("admin@fiap.com"), requisicao.destination().toAddresses());
        assertEquals("UTF-8", requisicao.message().body().text().charset());
        assertEquals("Relatório semanal de feedbacks - 04/11/2026 a 11/11/2026", requisicao.message().subject().data());

        String corpo = requisicao.message().body().text().data();
        // 11:00 em UTC equivale a 08:00 em Brasília
        assertTrue(corpo.contains("Período: 04/11/2026 às 08:00 até 11/11/2026 às 08:00"));
        assertTrue(corpo.contains("Total de avaliações: 2"));
        assertTrue(corpo.contains("Média das notas: 5,50"));
        assertTrue(corpo.contains("- 08/11/2026: 2"));
        assertTrue(corpo.contains("- 09/11/2026: 0"));
        assertTrue(corpo.contains("- CRITICA: 1"));
        assertTrue(corpo.contains("- MEDIA: 0"));
        assertTrue(corpo.contains("1. Descrição: Aula sem áudio | Urgência: CRITICA | Data de envio: 08/11/2026 às 11:32 | Nota: 2"));
        assertTrue(corpo.contains("2. Descrição: Ótima didática | Urgência: BAIXA | Data de envio: 08/11/2026 às 15:05 | Nota: 9"));
    }

    @Test
    void avisaQuandoNaoHouveAvaliacoes() {
        RelatorioSemanal vazio = new RelatorioSemanal(INICIO, FIM, 0, 0, Map.of(), Map.of(), List.of());

        servico(() -> SendEmailResponse.builder().messageId("id").build()).enviar(vazio);

        String corpo = requisicao.message().body().text().data();
        assertTrue(corpo.contains("Nenhuma avaliação foi recebida no período."));
        assertFalse(corpo.contains("Média das notas"));
    }

    @Test
    void propagaFalhaDoSes() {
        SesEmailService servico = servico(() -> {
            throw SesException.builder().message("throttled").build();
        });

        assertThrows(SesException.class, () -> servico.enviar(relatorioComDuasAvaliacoes()));
    }
}
