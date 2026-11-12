package relatorio_semanal.service;

import relatorio_semanal.domain.RelatorioSemanal;

public interface EmailSender {

    void enviar(RelatorioSemanal relatorio);
}
