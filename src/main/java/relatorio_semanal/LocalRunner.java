package relatorio_semanal;

import relatorio_semanal.handler.RelatorioSemanalHandler;
import relatorio_semanal.handler.TestContext;

import java.util.Map;

public class LocalRunner {

    public static void main(String[] args) {
        String resposta = new RelatorioSemanalHandler().handleRequest(Map.of(), new TestContext());

        System.out.println("--- RESULTADO DO TESTE LOCAL ---");
        System.out.println(resposta);
    }
}
