# Correção de segurança 2.2.2 — 23/11/2026

## Componentes removidos

O gerador não precisa de Spring/JPA para produzir seus templates. Spring Context,
Spring Web, Spring Data JPA e Jakarta Persistence estavam declarados em escopo de
teste, sem uso pelos testes; foram removidos. Isso não significa que o Spring
estivesse incorporado no JAR distribuído: o Maven Shade já excluía o escopo test.

Log4j API e Core estavam no runtime e eram incorporados ao JAR executável. Havia
somente duas declarações de logger sem chamadas. As declarações e as dependências
foram removidas, sem substituir por outro framework de logging.

| Alertas reportados | Componente | Tratamento |
| --- | --- | --- |
| CVE-2026-49844 | Log4j API | Dependência e classes removidas do artefato |
| CVE-2026-34480, CVE-2026-34479, CVE-2026-34478, CVE-2026-34477, CVE-2025-68161 | Log4j Core/bridge, conforme o alerta | API/Core removidos; bridge Log4j 1 também não está presente |
| CVE-2025-41234, CVE-2025-22233, CVE-2024-38820, CVE-2024-38809, CVE-2024-22262, CVE-2024-22259, CVE-2024-22243 | Spring Framework | Removidas as dependências de teste que traziam o Framework |

Os alertas identificam versões/componentes afetados; isso não prova exploração
no gerador. O tratamento desta versão remove os componentes desnecessários em vez
de manter versões atualizadas sem utilização.

## Validação e alcance

- Build de release com testes, fontes e Javadoc.
- Árvore de dependências sem Spring, Spring Data, Jakarta Persistence ou Log4j.
- Conferência do JAR executável e de seu POM embutido, incluindo ausência dos pacotes de classes desses componentes.
- Execução standalone da validação `.gonthera`.

A mudança não atualiza dependências dos projetos consumidores. Código gerado Java
continua usando Spring/JPA; quem executa uma aplicação Spring deve atualizar o
próprio Spring Boot/BOM. As versões antigas do Gonthera publicadas no Central são
imutáveis: consumidores devem atualizar para `br.com.smartverse:gonthera-cli:2.2.2`.
Não se trata de uma auditoria completa de todas as dependências e plugins de build.

A entrega inclui as alterações .NET 10 da 2.2.1 que estavam em desenvolvimento no
mesmo workspace; os contratos Java/Node não mudam por esta limpeza de dependências.

## Fontes

- [Avisos de segurança do Apache Logging](https://logging.apache.org/security.html)
- [Spring: CVE-2025-41234](https://spring.io/security/cve-2025-41234/)
- [Spring: CVE-2025-22233](https://spring.io/security/cve-2025-22233/)
- [Spring: CVE-2024-38820](https://spring.io/security/cve-2024-38820/)
- [Spring: CVE-2024-38809](https://spring.io/security/cve-2024-38809/)
- [Spring: CVE-2024-22262](https://spring.io/security/cve-2024-22262/)
- [Spring: CVE-2024-22259](https://spring.io/security/cve-2024-22259/)
- [Spring: CVE-2024-22243](https://spring.io/security/cve-2024-22243/)
