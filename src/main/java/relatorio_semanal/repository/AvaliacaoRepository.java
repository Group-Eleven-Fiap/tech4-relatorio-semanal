package relatorio_semanal.repository;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import relatorio_semanal.domain.Avaliacao;

import java.util.List;

public interface AvaliacaoRepository {

    List<Avaliacao> buscarTodas(LambdaLogger logger);
}
