package relatorio_semanal.repository;

import org.junit.jupiter.api.Test;
import relatorio_semanal.domain.Avaliacao;
import relatorio_semanal.handler.TestContext;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DynamoDbAvaliacaoRepositoryTest {

    private final List<ScanRequest> requisicoes = new ArrayList<>();

    private DynamoDbAvaliacaoRepository repositorio(ScanResponse... paginas) {
        Queue<ScanResponse> fila = new ArrayDeque<>(List.of(paginas));
        DynamoDbClient cliente = new DynamoDbClient() {
            @Override
            public ScanResponse scan(ScanRequest request) {
                requisicoes.add(request);
                return fila.remove();
            }

            @Override public String serviceName() { return "dynamodb"; }
            @Override public void close() { }
        };
        return new DynamoDbAvaliacaoRepository(cliente, "Avaliacoes");
    }

    // Mesmos atributos gravados pelo AvaliacaoRepository do tech4-ingestao-avaliacao
    private static Map<String, AttributeValue> item(String id, String nota, String dataEnvio) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("descricao", AttributeValue.builder().s("Aula sem áudio").build());
        item.put("urgencia", AttributeValue.builder().s("CRITICA").build());
        if (nota != null) {
            item.put("nota", AttributeValue.builder().n(nota).build());
        }
        item.put("dataEnvio", AttributeValue.builder().s(dataEnvio).build());
        return item;
    }

    @Test
    void converteOItemGravadoPelaIngestao() {
        List<Avaliacao> avaliacoes = repositorio(
                ScanResponse.builder().items(item("1", "2", "2026-11-08T14:32:01.169")).build())
                .buscarTodas(new TestContext().getLogger());

        assertEquals(List.of(new Avaliacao("1", "Aula sem áudio", 2, "CRITICA", LocalDateTime.parse("2026-11-08T14:32:01.169"))),
                avaliacoes);
        assertEquals("Avaliacoes", requisicoes.get(0).tableName());
    }

    @Test
    void leTodasAsPaginasDoScan() {
        Map<String, AttributeValue> chaveDaPagina = Map.of("id", AttributeValue.builder().s("1").build());

        List<Avaliacao> avaliacoes = repositorio(
                ScanResponse.builder().items(item("1", "2", "2026-11-08T14:32:01")).lastEvaluatedKey(chaveDaPagina).build(),
                ScanResponse.builder().items(item("2", "9", "2026-11-09T10:00:00")).build())
                .buscarTodas(new TestContext().getLogger());

        assertEquals(List.of("1", "2"), avaliacoes.stream().map(Avaliacao::id).toList());
        assertEquals(2, requisicoes.size());
        assertFalse(requisicoes.get(0).hasExclusiveStartKey());
        assertEquals(chaveDaPagina, requisicoes.get(1).exclusiveStartKey());
    }

    @Test
    void ignoraItemComDadosInvalidosSemPerderOsDemais() {
        List<Avaliacao> avaliacoes = repositorio(ScanResponse.builder().items(
                        item("sem-nota", null, "2026-11-08T14:32:01"),
                        item("data-ruim", "5", "ontem"),
                        item("ok", "5", "2026-11-08T14:32:01")).build())
                .buscarTodas(new TestContext().getLogger());

        assertEquals(List.of("ok"), avaliacoes.stream().map(Avaliacao::id).toList());
    }
}
