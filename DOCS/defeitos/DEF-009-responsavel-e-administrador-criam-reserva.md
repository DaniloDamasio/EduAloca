# DEF-009 — Responsável e Administrador criam reserva

| Campo | Valor |
|---|---|
| Status | **Aberto** |
| Severidade | Média: perfis sem a operação no escopo (RF-12: SOL) criam reservas; o Administrador pode ocupar qualquer recurso |
| Prioridade | Média |
| Requisitos | RF-02, RF-12 |
| Componente | `ReservaController.criar` / `ReservaService.criar` |
| Encontrado em | 11/11/2026, branch `relatorio-utilizacao`, commit `9c71028` (visível só depois do DEF-003) |
| Matriz | D-03, linha 3 |
| Teste que detecta | `MatrizAutorizacaoApiTest` :: `celulaDaMatriz` (linha 3, RESPONSAVEL e ADMINISTRADOR) |

## Resumo

`POST /reservas` só exige autenticação. Qualquer perfil cria reserva; o RF-12 restringe a criação ao Solicitante.
Até o DEF-003 o defeito não aparecia pela API, porque nenhum login emitia outro perfil.

## Passos para reproduzir

1. `POST /auth/login` como `responsavel@example.com` (ou `admin@example.com`).
2. `POST /reservas` com sala livre, `inicio` 2027-03-12T08:00 e `fim` 2027-03-12T10:00.

```bash
./mvnw -B test -Djacoco.skip=true -Dtest=MatrizAutorizacaoApiTest
```

## Resultado esperado

`403` para Responsável e Administrador; `201` só para Solicitante.

## Resultado obtido

```
linha 3 POST /reservas como RESPONSAVEL (NENHUM):
  corpo {"id":"...","solicitanteId":"00000000-0000-0000-0000-0000000000a1","estado":"APROVADA",...}
  ==> expected: <403> but was: <201>
linha 3 POST /reservas como ADMINISTRADOR (NENHUM): ... ==> expected: <403> but was: <201>
```

## Causa raiz

Nenhuma checagem de perfil em [ReservaService.java:48-57](../../src/main/java/com/edualoca/domain/reserva/ReservaService.java#L48-L57).

## Ambiente

- Java 21.0.9 (Temurin), Spring Boot 4.1.1, perfil padrão em memória
- MockMvc sobre o contexto completo (`@SpringBootTest`); tokens obtidos por `POST /auth/login`

## Correção

Pendente.

## Reteste

Pendente.
