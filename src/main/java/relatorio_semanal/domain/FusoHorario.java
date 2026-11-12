package relatorio_semanal.domain;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class FusoHorario {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private FusoHorario() {
    }

    // As datas são gravadas em UTC e mostradas ao administrador no horário de Brasília
    public static LocalDateTime paraBrasilia(LocalDateTime dataEmUtc) {
        return dataEmUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BRASILIA).toLocalDateTime();
    }
}
