package relatorio_semanal.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import relatorio_semanal.domain.Avaliacao;
import relatorio_semanal.domain.FusoHorario;
import relatorio_semanal.domain.RelatorioSemanal;
import relatorio_semanal.repository.AvaliacaoRepository;
import relatorio_semanal.repository.DynamoDbAvaliacaoRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RelatorioService {

    static final int DIAS_DO_PERIODO = 7;

    // Mesmos nomes do UrgenciaEnum da ingestão; aparecem no relatório mesmo com quantidade zero
    private static final List<String> URGENCIAS = List.of("CRITICA", "MEDIA", "BAIXA");

    private final AvaliacaoRepository repository;
    private final EmailSender emailSender;
    private final Clock clock;

    public RelatorioService() {
        this(new DynamoDbAvaliacaoRepository(), new SesEmailService(), Clock.systemUTC());
    }

    public RelatorioService(AvaliacaoRepository repository, EmailSender emailSender, Clock clock) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    public RelatorioSemanal gerarEEnviar(LambdaLogger logger) {
        // 1. Período: os últimos 7 dias até o momento da execução (em UTC, como a ingestão grava)
        LocalDateTime fim = LocalDateTime.now(clock);
        LocalDateTime inicio = fim.minusDays(DIAS_DO_PERIODO);

        // 2. Busca as avaliações e mantém só as do período
        List<Avaliacao> avaliacoes = repository.buscarTodas(logger).stream()
                .filter(avaliacao -> !avaliacao.dataEnvio().isBefore(inicio) && avaliacao.dataEnvio().isBefore(fim))
                .sorted(Comparator.comparing(Avaliacao::dataEnvio))
                .toList();

        // 3. Calcula os números do relatório
        RelatorioSemanal relatorio = new RelatorioSemanal(
                inicio,
                fim,
                avaliacoes.size(),
                avaliacoes.stream().mapToInt(Avaliacao::nota).average().orElse(0),
                contarPorDia(avaliacoes, inicio, fim),
                contarPorUrgencia(avaliacoes),
                avaliacoes);

        logger.log(String.format("[INFO] relatorio.gerado total=%d", relatorio.totalAvaliacoes()));

        // 4. Envia por e-mail ao administrador
        emailSender.enviar(relatorio);

        return relatorio;
    }

    private Map<LocalDate, Long> contarPorDia(List<Avaliacao> avaliacoes, LocalDateTime inicio, LocalDateTime fim) {
        Map<LocalDate, Long> porDia = new LinkedHashMap<>();

        // Todos os dias do período aparecem, mesmo os que não tiveram avaliação
        LocalDate ultimoDia = FusoHorario.paraBrasilia(fim).toLocalDate();
        for (LocalDate dia = FusoHorario.paraBrasilia(inicio).toLocalDate(); !dia.isAfter(ultimoDia); dia = dia.plusDays(1)) {
            porDia.put(dia, 0L);
        }

        for (Avaliacao avaliacao : avaliacoes) {
            porDia.merge(FusoHorario.paraBrasilia(avaliacao.dataEnvio()).toLocalDate(), 1L, Long::sum);
        }
        return porDia;
    }

    private Map<String, Long> contarPorUrgencia(List<Avaliacao> avaliacoes) {
        Map<String, Long> porUrgencia = new LinkedHashMap<>();
        URGENCIAS.forEach(urgencia -> porUrgencia.put(urgencia, 0L));

        for (Avaliacao avaliacao : avaliacoes) {
            String urgencia = avaliacao.urgencia() == null ? "SEM_URGENCIA" : avaliacao.urgencia();
            porUrgencia.merge(urgencia, 1L, Long::sum);
        }
        return porUrgencia;
    }
}
