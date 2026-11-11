# DEF-010 — Responsável de outro bloco aprova e rejeita reserva restrita

| Campo | Valor |
|---|---|
| Status | **Aberto** |
| Severidade | Alta: qualquer Responsável decide reserva de recurso que não é seu, contra a regra crítica “Somente responsáveis aprovam recursos restritos” lida como Responsável competente |
| Prioridade | Alta |
| Requisitos | RF-18, RF-19, RF-32 (regra P-2 da matriz) |
| Componente | `ReservaService.aprovar` / `rejeitar` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `9c71028` (visível só depois do DEF-003) |
| Matriz | D-05, linhas 7 e 8 |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (linhas 7 e 8, RESPONSAVEL_NAO_COMPETENTE) |

## Resumo

A aprovação e a rejeição checam só o perfil (Responsável ou Administrador). O vínculo Responsável↔localização existe em
`Banco.responsavelPorLocalizacao`, mas só a notificação (RF-32) o usa. O Responsável do Bloco B aprova reserva de
laboratório do Bloco A. O Administrador continua podendo decidir qualquer reserva (RF-19, decisão de 11/11/2026).

## Passos para reproduzir

1. Como `usuario@example.com`, reservar o "Lab Restrito" (Bloco A, restrito): nasce `SOLICITADA`.
2. Como `responsavel.b@example.com` (Bloco B), `POST /reservas/{id}/aprovacao`.
3. Repetir com `POST /reservas/{id}/rejeicao` e `{"justificativa":"laboratorio reservado para banca"}`.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`403` para o Responsável do Bloco B; `200` para o Responsável do Bloco A e para o Administrador.

## Resultado obtido

```
linha 7 POST /reservas/{id}/aprovacao como RESPONSAVEL_NAO_COMPETENTE (RESTRITA):
  corpo {"id":"...","estado":"APROVADA",...} ==> expected: <403> but was: <200>
linha 8 POST /reservas/{id}/rejeicao como RESPONSAVEL_NAO_COMPETENTE (RESTRITA):
  corpo {"id":"...","estado":"REJEITADA",...} ==> expected: <403> but was: <200>
```

## Causa raiz

[ReservaService.java:141-170](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L141-L170): checagem
de perfil sem checagem de competência.

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

Pendente.

## Reteste

Pendente.
