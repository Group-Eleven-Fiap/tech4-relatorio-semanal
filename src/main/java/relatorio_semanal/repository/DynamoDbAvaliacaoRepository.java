package relatorio_semanal.repository;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import relatorio_semanal.domain.Avaliacao;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DynamoDbAvaliacaoRepository implements AvaliacaoRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbAvaliacaoRepository() {
        this(criarCliente(), obterNomeDaTabela());
    }

    DynamoDbAvaliacaoRepository(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    // A tabela tem só a chave "id", então lemos tudo (scan) e o serviço filtra o período.
    @Override
    public List<Avaliacao> buscarTodas(LambdaLogger logger) {
        List<Avaliacao> avaliacoes = new ArrayList<>();
        Map<String, AttributeValue> proximaPagina = null;

        // O scan devolve no máximo 1 MB por chamada; seguimos até não haver mais páginas
        do {
            ScanResponse resposta = dynamoDbClient.scan(ScanRequest.builder()
                    .tableName(tableName)
                    .exclusiveStartKey(proximaPagina)
                    .build());

            for (Map<String, AttributeValue> item : resposta.items()) {
                try {
                    avaliacoes.add(converter(item));
                } catch (RuntimeException e) {
                    // Um registro com problema não pode impedir o relatório dos demais
                    logger.log(String.format("[ALERTA] relatorio.item_ignorado id=%s erro=%s",
                            texto(item, "id"), e.getClass().getSimpleName()));
                }
            }

            proximaPagina = resposta.hasLastEvaluatedKey() ? resposta.lastEvaluatedKey() : null;
        } while (proximaPagina != null);

        return avaliacoes;
    }

    private Avaliacao converter(Map<String, AttributeValue> item) {
        return new Avaliacao(
                texto(item, "id"),
                texto(item, "descricao"),
                Integer.parseInt(item.get("nota").n()),
                texto(item, "urgencia"),
                LocalDateTime.parse(item.get("dataEnvio").s()));
    }

    private static String texto(Map<String, AttributeValue> item, String campo) {
        AttributeValue valor = item.get(campo);
        return valor == null ? null : valor.s();
    }

    private static String obterNomeDaTabela() {
        String nome = System.getenv("TABELA_AVALIACOES");
        return nome != null && !nome.isBlank() ? nome : "Avaliacoes";
    }

    private static DynamoDbClient criarCliente() {
        String customEndpoint = System.getenv("AWS_ENDPOINT_URL");

        var builder = DynamoDbClient.builder()
                .region(Region.US_EAST_1);

        // Configuração caso esteja rodando localmente com LocalStack
        if (customEndpoint != null && !customEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(customEndpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")
            ));
        }

        return builder.build();
    }
}
