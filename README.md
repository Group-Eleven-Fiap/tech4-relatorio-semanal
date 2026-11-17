# Relatório Semanal

Serviço de relatório do Tech Challenge — Fase 4. Uma função AWS Lambda, disparada uma vez por semana, lê as avaliações gravadas pelo [tech4-ingestao-avaliacao](https://github.com/Group-Eleven-Fiap/tech4-ingestao-avaliacao), calcula os números da semana e envia o relatório ao administrador por e-mail via Amazon SES.

## Modelo de cloud e arquitetura

A solução usa **serverless (FaaS) em nuvem pública AWS**: não há servidor para administrar e o custo acompanha o uso. Esta função roda poucos segundos por semana.

```text
EventBridge Scheduler (segunda-feira, 08h de Brasília)
  │ a AWS invoca a função
  ▼
RelatorioSemanalHandler (Lambda)
  ▼
RelatorioService ──► DynamoDbAvaliacaoRepository ──► DynamoDB: Avaliacoes (somente leitura)
  │ filtra os últimos 7 dias e calcula média e quantidades
  ▼
SesEmailService ──► Amazon SES ──► e-mail do administrador

Erro na execução ──► Alarme CloudWatch ──► SNS ──► e-mail
```

| Componente | Motivo |
|------------|--------|
| AWS Lambda (Java 21) | Executa só quando o agendamento dispara. |
| EventBridge Scheduler | Agendamento com fuso horário e novas tentativas em caso de falha. |
| DynamoDB | Tabela `Avaliacoes`, criada e preenchida pela ingestão. |
| Amazon SES | Envio de e-mail autenticado por IAM, sem senha no código. |
| CloudWatch + X-Ray | Logs, métricas, alarmes, dashboard e rastreamento. |
| AWS SAM + GitHub Actions | Infraestrutura como código e deploy automatizado. |

Este repositório faz parte de uma solução com três serviços, implantados nesta ordem:

| Ordem | Repositório | Responsabilidade |
|-------|-------------|------------------|
| 1 | [tech4-notificacao-urgencia](https://github.com/Group-Eleven-Fiap/tech4-notificacao-urgencia) | Criar a fila e enviar o e-mail de aviso quando a avaliação é crítica. |
| 2 | [tech4-ingestao-avaliacao](https://github.com/Group-Eleven-Fiap/tech4-ingestao-avaliacao) | Receber, validar, classificar e gravar a avaliação (cria a tabela). |
| 3 | **tech4-relatorio-semanal** (este) | Enviar o relatório semanal com média e quantidades. |

## Documentação da função

**Responsabilidade única:** transformar as avaliações da semana em um e-mail de relatório. Não recebe avaliações, não calcula urgência e não altera a tabela.

| Classe | Responsabilidade |
|--------|------------------|
| `handler.RelatorioSemanalHandler` | Entrada da Lambda; registra início, sucesso ou falha. |
| `service.RelatorioService` | Define o período, filtra as avaliações e calcula média e quantidades. |
| `repository.AvaliacaoRepository` / `repository.DynamoDbAvaliacaoRepository` | Contrato de leitura e implementação com DynamoDB. |
| `service.EmailSender` / `service.SesEmailService` | Contrato de envio e implementação com SES. |
| `service.EmailTemplateService` | Monta assunto e corpo do e-mail. |
| `domain.Avaliacao` / `domain.RelatorioSemanal` | Modelos da avaliação lida e do relatório gerado. |
| `domain.FusoHorario` | Converte as datas de UTC para o horário de Brasília. |

Ponto de entrada: `relatorio_semanal.handler.RelatorioSemanalHandler::handleRequest`

### Período do relatório

O relatório cobre **os 7 dias anteriores ao momento da execução**. O conteúdo do evento não é usado, então a função pode ser disparada manualmente a qualquer hora, o que é útil na demonstração. Como o agendamento roda sempre no mesmo horário, os relatórios de semanas seguidas não deixam avaliações de fora nem as repetem.

### Dados lidos

Atributos gravados pelo `AvaliacaoRepository` da ingestão na tabela `Avaliacoes`: `id`, `descricao`, `nota`, `urgencia` e `dataEnvio`.

`dataEnvio` é tratado como UTC, que é o relógio da Lambda de ingestão, e exibido no horário de Brasília. A quantidade por dia também segue o dia de Brasília.

A tabela tem apenas a chave `id`, então a função lê todos os registros (`Scan`, com paginação) e filtra o período no código. Isso atende bem ao volume do projeto. Com um volume grande, o caminho é criar na tabela um índice por data e trocar o `Scan` por `Query`.

Um registro com `nota` ou `dataEnvio` inválidos é ignorado, registrado em log (`relatorio.item_ignorado`) e dispara um alarme, sem impedir o relatório dos demais.

### E-mail enviado

```text
Assunto: Relatório semanal de feedbacks - 04/11/2026 a 11/11/2026

Relatório semanal de feedbacks
Período: 04/11/2026 às 08:00 até 11/11/2026 às 08:00 (horário de Brasília)

Total de avaliações: 3
Média das notas: 5,33

Quantidade de avaliações por dia:
- 04/11/2026: 0
- 05/11/2026: 2
...
- 11/11/2026: 1

Quantidade de avaliações por urgência:
- CRITICA: 1
- MEDIA: 1
- BAIXA: 1

Avaliações recebidas:
1. Descrição: Vídeo travando o tempo todo | Urgência: CRITICA | Data de envio: 05/11/2026 às 11:32 | Nota: 1
2. Descrição: Aula razoável, faltaram exemplos | Urgência: MEDIA | Data de envio: 05/11/2026 às 15:05 | Nota: 6
3. Descrição: Conteúdo excelente | Urgência: BAIXA | Data de envio: 11/11/2026 às 07:40 | Nota: 9
```

Sem avaliações no período, o e-mail é enviado mesmo assim, com o aviso "Nenhuma avaliação foi recebida no período."

### Tratamento de falhas

Se a leitura da tabela ou o envio do e-mail falhar, a execução termina com erro. O agendador tenta de novo até 2 vezes dentro de 1 hora, e o alarme `relatorio-semanal-erros-lambda` avisa o administrador.

## Integração com a ingestão

Este repositório não cria a tabela: ela pertence à ingestão, que deve ser implantada antes, na mesma conta e na região `us-east-1`. O nome da tabela é o parâmetro `TabelaAvaliacoes` (padrão `Avaliacoes`).

## Configuração

| Variável | Uso | Obrigatória |
|----------|-----|-------------|
| `ADMIN_EMAIL` | Destinatário do relatório. | Sim |
| `EMAIL_REMETENTE` | Remetente, verificado no SES. | Sim |
| `TABELA_AVALIACOES` | Nome da tabela. Padrão: `Avaliacoes`. | Não |
| `AWS_ENDPOINT_URL` | Endpoint alternativo para testes locais. Não é definido na AWS. | Não |

## Segurança e governança

- Role IAM exclusiva da função: somente `dynamodb:Scan` nesta tabela (leitura), envio de e-mail somente pelas identidades configuradas e escrita somente no próprio log group.
- Deploy via OIDC: nenhuma chave da AWS fica guardada no GitHub.
- Nenhuma senha no código: o SES é autorizado pela role da função.
- O conteúdo das avaliações não é registrado em log.

## Monitoramento

| Recurso | O que mostra |
|---------|--------------|
| Logs `/aws/lambda/relatorio-semanal` | `relatorio.inicio`, `relatorio.gerado`, `relatorio.sucesso`, `relatorio.falha`, com retenção de 14 dias. |
| Alarme `relatorio-semanal-erros-lambda` | Relatório que não foi gerado ou enviado. |
| Alarme `relatorio-semanal-itens-ignorados` | Avaliação com dados inválidos que ficou fora do relatório. |
| Dashboard `relatorio-semanal` | Invocações, erros e duração. |
| AWS X-Ray | Rastreamento de cada execução. |

Os alarmes avisam o `AdminEmail` pelo tópico SNS `relatorio-semanal-alarmes`. A AWS envia um e-mail de confirmação da inscrição, que precisa ser aceito.

## Deploy

Pré-requisitos, feitos uma única vez na conta AWS e compartilhados com o [tech4-notificacao-urgencia](https://github.com/Group-Eleven-Fiap/tech4-notificacao-urgencia):

1. Verificar `EMAIL_REMETENTE` e `ADMIN_EMAIL` no SES, região `us-east-1`. A conta começa em sandbox, e sem isso o envio é recusado.
2. Criar a role de deploy `tech4-github-deploy` com o `infra/github-oidc.yaml` do repositório de notificação. Ela vale para todos os repositórios `tech4-*` da organização.
3. Neste repositório, em *Settings → Secrets and variables → Actions → Variables*, criar `AWS_ROLE_ARN`, `ADMIN_EMAIL` e `EMAIL_REMETENTE`.

Depois disso, cada push na `main` roda os testes e, se passarem, executa `sam build` e `sam deploy`. Enquanto `AWS_ROLE_ARN` não existir, o pipeline roda só os testes.

Deploy manual:

```powershell
sam build
sam deploy --stack-name tech4-relatorio-semanal --region us-east-1 --resolve-s3 --capabilities CAPABILITY_IAM --parameter-overrides AdminEmail=admin@exemplo.com EmailRemetente=remetente@exemplo.com
```

Disparo manual, sem esperar a segunda-feira:

```powershell
aws lambda invoke --function-name relatorio-semanal --region us-east-1 --cli-binary-format raw-in-base64-out --payload '{}' saida.json
```

O e-mail chega ao administrador e o log `/aws/lambda/relatorio-semanal` registra `relatorio.sucesso`. Como o período é sempre os 7 dias anteriores à execução, o disparo manual gera o mesmo tipo de relatório do agendamento.

## Build e testes

Pré-requisito: JDK 21.

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

São 12 testes unitários: leitura da tabela (inclusive todas as páginas do `Scan` e itens inválidos), média e quantidades por dia e por urgência, período de 7 dias, dia no horário de Brasília, relatório sem avaliações, envio pelo SES e falhas. Nenhum deles acessa a AWS. Eles rodam no pipeline antes de cada deploy.

## Verificação após o deploy

```powershell
aws cloudformation describe-stacks --stack-name tech4-relatorio-semanal --region us-east-1 --query "Stacks[0].StackStatus"
aws scheduler get-schedule --name relatorio-semanal-agendamento --region us-east-1 --query "[ScheduleExpression,ScheduleExpressionTimezone,State]"
aws logs tail /aws/lambda/relatorio-semanal --region us-east-1 --since 15m
```

O agendamento deve estar `ENABLED`, com `cron(0 8 ? * MON *)` e `America/Sao_Paulo`. Para ver um relatório sem esperar a segunda-feira, use o disparo manual da seção *Deploy*.

## Execução local

O `LocalRunner` chama o handler sem implantar nada. Ele usa a AWS de verdade: **lê a tabela `Avaliacoes` (somente leitura) e envia um e-mail real pelo SES**. Para isso, é preciso ter credenciais da AWS na máquina (por exemplo, `aws login`) com permissão de `dynamodb:Scan` nessa tabela e de envio pelo SES, a tabela já criada pela ingestão e os dois endereços verificados no SES, em `us-east-1`:

```powershell
$env:AWS_REGION = "us-east-1"
$env:EMAIL_REMETENTE = "remetente@exemplo.com"
$env:ADMIN_EMAIL = "admin@exemplo.com"
```

Depois, execute `relatorio_semanal.LocalRunner` pela IDE. Com a tabela vazia, o relatório sai com o aviso de que não houve avaliações.
