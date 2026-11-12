package relatorio_semanal.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import relatorio_semanal.domain.RelatorioSemanal;
import relatorio_semanal.service.RelatorioService;

import java.util.Map;

/**
 * Disparada pelo agendamento semanal do EventBridge. O conteúdo do evento não é usado:
 * o período é sempre os últimos 7 dias, então a função também pode ser invocada manualmente.
 */
public class RelatorioSemanalHandler implements RequestHandler<Map<String, Object>, String> {

    private final RelatorioService relatorioService;

    public RelatorioSemanalHandler() {
        this(new RelatorioService());
    }

    RelatorioSemanalHandler(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @Override
    public String handleRequest(Map<String, Object> evento, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("[INFO] relatorio.inicio");

        try {
            RelatorioSemanal relatorio = relatorioService.gerarEEnviar(logger);

            logger.log(String.format("[SUCESSO] relatorio.sucesso total=%d", relatorio.totalAvaliacoes()));
            return String.format("Relatório enviado com %d avaliações.", relatorio.totalAvaliacoes());
        } catch (RuntimeException e) {
            // Relança para a execução terminar com erro: o agendador tenta de novo e o alarme dispara
            logger.log(String.format("[ERRO_RELATORIO] relatorio.falha erro=%s", e.getClass().getSimpleName()));
            throw e;
        }
    }
}
