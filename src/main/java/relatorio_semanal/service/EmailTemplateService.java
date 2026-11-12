package relatorio_semanal.service;

import relatorio_semanal.domain.Avaliacao;
import relatorio_semanal.domain.FusoHorario;
import relatorio_semanal.domain.RelatorioSemanal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EmailTemplateService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    public String criarAssunto(RelatorioSemanal relatorio) {
        return String.format("Relatório semanal de feedbacks - %s a %s",
                FusoHorario.paraBrasilia(relatorio.inicio()).format(DIA),
                FusoHorario.paraBrasilia(relatorio.fim()).format(DIA));
    }

    public String criarCorpo(RelatorioSemanal relatorio) {
        StringBuilder corpo = new StringBuilder();

        corpo.append(String.format("Relatório semanal de feedbacks%n"));
        corpo.append(String.format("Período: %s até %s (horário de Brasília)%n%n",
                formatar(relatorio.inicio()), formatar(relatorio.fim())));

        if (relatorio.totalAvaliacoes() == 0) {
            corpo.append(String.format("Nenhuma avaliação foi recebida no período.%n"));
            return corpo.toString();
        }

        corpo.append(String.format("Total de avaliações: %d%n", relatorio.totalAvaliacoes()));
        corpo.append(String.format(BRASIL, "Média das notas: %.2f%n%n", relatorio.mediaNotas()));

        corpo.append(String.format("Quantidade de avaliações por dia:%n"));
        relatorio.quantidadePorDia().forEach((dia, quantidade) ->
                corpo.append(String.format("- %s: %d%n", dia.format(DIA), quantidade)));

        corpo.append(String.format("%nQuantidade de avaliações por urgência:%n"));
        relatorio.quantidadePorUrgencia().forEach((urgencia, quantidade) ->
                corpo.append(String.format("- %s: %d%n", urgencia, quantidade)));

        corpo.append(String.format("%nAvaliações recebidas:%n"));
        int numero = 1;
        for (Avaliacao avaliacao : relatorio.avaliacoes()) {
            corpo.append(String.format("%d. Descrição: %s | Urgência: %s | Data de envio: %s | Nota: %d%n",
                    numero++, avaliacao.descricao(), avaliacao.urgencia(), formatar(avaliacao.dataEnvio()), avaliacao.nota()));
        }

        return corpo.toString();
    }

    private String formatar(LocalDateTime dataEmUtc) {
        return FusoHorario.paraBrasilia(dataEmUtc).format(DIA_E_HORA);
    }
}
