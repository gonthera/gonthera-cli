# Gonthera CLI .NET 10 — referência técnica, 2.2.1 em desenvolvimento

Leia [o guia de geração .NET](../DOTNET.md) para o contrato atualizado de saída,
customização, DI, dependências e migração. Esta referência substitui o layout `_gen`
da 2.1.4. Configuração JSON, tipos e regras de relacionamento permanecem nos modelos,
validadores e templates como fontes de verdade.

## Implementação

- `service/dotNet/GenerateDotNet` renderiza os templates em `target/gonthera-dotnet-staging`.
- `service/dotNet/DotNetProjectLayout` distribui os fontes nas quatro camadas e cria scaffolds ausentes.
- Templates em `src/main/resources/xsd/dotnet` definem bases de service/controller/contexto, arquivos manuais e `.csproj`.
- Não coloque código de negócio em `Generated`; toda essa saída é apagada na regeneração.
- Nunca sobrescreva customizações, arquivos de projeto ou `Program.cs` existentes.
- Teste regeneração, preservação das customizações e compilação com SDK .NET 10.
- Teste a DI resolvendo as classes concretas customizadas, incluindo overrides de service e repository.
- Classes removidas dos contratos podem deixar arquivos manuais que precisam de revisão pelo consumidor.
- Não reintroduza dependências de `DataOnBackend`, `DBMigrator` ou `ApiDbContext` externo.

## Contratos preservados

A configuração `.gonthera` e a validação estrutural continuam compartilhadas com os
outros alvos. Os contratos de campos, relacionamentos, endpoints e RabbitMQ permanecem
no [README](../../README.md). O CRUD mantém os métodos e consultas existentes; a
mudança desta versão é a separação de responsabilidades e a fronteira de customização.
Paginação/filtros continuam no repository de infraestrutura; não prometa novos
operadores ou alteração de semântica. Autenticação, migrations e produção são manuais.

## Configuração modular

```text
.gonthera/
├── project.json
├── entities.json
├── endpoints.json
├── enums.json
└── messaging.json
```

`.gonthera/project.json`:

```json
{
  "mainPackage": "CustomerService",
  "projectName": "customer-service",
  "language": "DOTNET"
}
```

Os arquivos de coleções contêm arrays diretamente. Arquivos separados sobrescrevem
apenas suas seções. Não adicione `database`: o validador rejeita esse objeto quando
a linguagem não é Node. `authorization.json` configura classes exclusivas do Java.

No formato único:

```json
{
  "mainPackage": "CustomerService",
  "projectName": "customer-service",
  "language": "DOTNET",
  "entities": [],
  "endpoints": [],
  "enums": []
}
```

## Contrato de entidade

```json
{
  "comment": "Cadastro de clientes",
  "entityName": "customer",
  "tableName": "customer",
  "classExtends": "",
  "generateDefaultControllers": true,
  "controllerAbstract": false,
  "serviceAbstract": false,
  "onlyDTO": false,
  "entityFields": [
    {
      "comment": "Identificador",
      "fieldName": "id",
      "list": false,
      "fieldProperties": {
        "fieldType": "uuid",
        "required": true,
        "valueDefault": ""
      },
      "metadata": {
        "nullable": false,
        "key": true
      }
    }
  ]
}
```

Semântica relevante:

- `entityName`: base de Entity, DTO, Repository e Controller.
- `tableName`: tabela PostgreSQL; omissão pode derivar snake_case.
- `generateDefaultControllers`: padrão `true`.
- `controllerAbstract`: padrão `false`; apenas afeta o Controller.
- `serviceAbstract`: Java-only; não gera Service C#.
- `onlyDTO`: existe no modelo, mas o fluxo .NET atual ainda gera outros artefatos;
  não prometa comportamento Java neste alvo.
- `classExtends`: não é aplicado de forma confiável.

Aliases `generateDefaultHandlers` e `handlerAbstract` são aceitos com warning; nomes
novos prevalecem. O par antigo `Handler/HandlerImpl` não é mais gerado.

## Campos e tipos

| `fieldType` | Tipo C# observado | PostgreSQL |
| --- | --- | --- |
| `uuid` | `Guid?` | `uuid` |
| `string`, `password` | `string` | `varchar` |
| `datetime`, `date` | `DateTime` | `timestamp` / `date` |
| `int` | `int?` | `integer`, PK pode virar `serial` |
| `integer` | `int` | `integer` |
| `long` | `Long` no mapper compartilhado | `integer` no SQL atual |
| `decimal`, `double` | `Double` | `numeric` |
| `boolean` | `bool` | `boolean` |

Tipos `byte`, `byte[]`, `inputStream` e `map` não possuem suporte .NET confiável.
Um tipo não primitivo/não enum é tratado como outra Entity/DTO. Enums são
reconhecidos sem diferenciar caixa no mapper, mas o parser de filtro usa
`Enum.Parse` com comportamento próprio.

O mapeamento nullable possui condições legadas frágeis; sempre compile uma fixture
com campos opcionais/obrigatórios após alterar tipos.


## Paginação e ResponseData

`RequestData` possui defaults:

```csharp
size = 10;
offset = 1;
filter = "";
order = "";
displayFields = "";
```

No repository atual:

- offset não positivo vira 1;
- `Skip((offset - 1) * size)` usa paginação one-based;
- `total` é número de registros filtrados;
- `offset` devolvido continua one-based;
- `size` da resposta recebe `totalPages`, não o tamanho solicitado.

Esse contrato difere de Java/Node e pode ser um defeito histórico. Não “corrija” em
documentação fingindo paridade; qualquer mudança exige decisão de compatibilidade.
Os campos `order` e `displayFields` existem em `RequestData`, mas o repository atual
não os aplica de forma efetiva: ordena fixamente por `Id` e `FindById` ignora
`displayFields`.

## DynamicFilter .NET

O dialeto implementa apenas `eq` e escolhe a primeira espécie de operador lógico
encontrada (`and` ou `or`). Não existe parser equivalente ao Java/Node.

Semântica:

- string: `ToLower().Contains(value.ToLower())`;
- Guid válido: igualdade;
- enum/outros: conversão/`Enum.Parse` e igualdade;
- relação escalar: caminho pontuado, seguindo propriedades C#;
- coleção: marcador `*`, por exemplo `children*.Description eq matriz`;
- coleção usa `Enumerable.Any`.

Limitações:

- sem `isNull`, `notNull`, ranges de data, `ne`, `in` ou parênteses;
- não há precedência lógica robusta;
- apenas uma família lógica por expressão;
- nomes devem corresponder à forma das propriedades C# geradas;
- null em string/caminho pode causar falhas;
- conversões numéricas/nullable e enums são frágeis;
- expressão sem condição válida pode acessar lista vazia.

O frontend deve selecionar o dialeto pela linguagem; filtros Java/Node não são
portáveis por garantia.

## Relacionamentos EF Core

O contrato JSON continua compartilhado:

```json
"relationShips": {
  "fetchType": "LAZY",
  "relationShip": "ManyToOne",
  "mappedBy": "",
  "bidirectional": false,
  "reference": true
}
```

- `bidirectional: false`: proprietário/FK.
- `bidirectional: true`: inverso; `mappedBy` aponta ao campo proprietário.
- `list: true`: coleções OneToMany/ManyToMany.
- Relações podem gerar tipos Entity e DTO e conversão correspondente.

Java/C# possuem geração de relações mais antiga e permissiva que a validação
específica do Node. Sempre gere e compile fixtures com OneToOne, OneToMany,
ManyToOne, ManyToMany e autorrelações antes de aceitar mudanças.

Lazy loading depende de `UseLazyLoadingProxies()` no DbContext atual. Isso influencia
serialização, ciclos e consultas; use DTOConverter, não retorne Entity diretamente.

## DTOConverter

O Controller usa métodos estáticos `ToEntity` e `ToDTO`. Customizações devem ficar
fora de `Generated` ou ser incorporadas ao template. Não edite converter gerado, pois o
diretório será apagado. Avalie cuidadosamente nullable, coleções e ciclos em relações.
