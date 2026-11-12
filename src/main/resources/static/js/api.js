/*
 * Cliente da API do EduAloca. O token fica em sessionStorage (some ao fechar a aba) e vai sempre no cabecalho
 * Authorization, nunca na URL. A autorizacao de verdade e do servidor: a interface so esconde o que o perfil nao usa.
 */
'use strict';

class ErroApi extends Error {
    constructor(status, mensagem) {
        super(mensagem);
        this.status = status;
    }
}

const Api = (() => {
    const CHAVE_TOKEN = 'edualoca.token';
    let aoExpirar = () => {};

    const token = () => sessionStorage.getItem(CHAVE_TOKEN);

    async function requisitar(metodo, caminho, corpo) {
        const cabecalhos = { Accept: 'application/json' };
        if (token()) {
            cabecalhos.Authorization = 'Bearer ' + token();
        }
        if (corpo !== undefined) {
            cabecalhos['Content-Type'] = 'application/json';
        }
        let resposta;
        try {
            resposta = await fetch(caminho, {
                method: metodo,
                headers: cabecalhos,
                body: corpo === undefined ? undefined : JSON.stringify(corpo),
                credentials: 'same-origin',
            });
        } catch (e) {
            throw new ErroApi(0, 'Não foi possível falar com o servidor. Verifique se a aplicação está rodando.');
        }
        if (resposta.status === 204) {
            return null;
        }
        const dados = await resposta.json().catch(() => null);
        if (!resposta.ok) {
            const mensagem = (dados && (dados.detail || dados.mensagem)) || 'Requisição não atendida (' + resposta.status + ')';
            if (resposta.status === 401 && token()) {
                sessionStorage.removeItem(CHAVE_TOKEN);
                aoExpirar();
            }
            throw new ErroApi(resposta.status, mensagem);
        }
        return dados;
    }

    /** Monta a query string com encodeURIComponent; parametros vazios sao omitidos. */
    function comParametros(caminho, parametros) {
        const pares = Object.entries(parametros)
            .filter(([, valor]) => valor !== undefined && valor !== null && String(valor).trim() !== '')
            .map(([chave, valor]) => encodeURIComponent(chave) + '=' + encodeURIComponent(String(valor).trim()));
        return pares.length ? caminho + '?' + pares.join('&') : caminho;
    }

    const id = (valor) => encodeURIComponent(valor);

    return {
        token,
        aoExpirar: (fn) => { aoExpirar = fn; },
        async entrar(email, senha) {
            const r = await requisitar('POST', '/auth/login', { email, senha });
            sessionStorage.setItem(CHAVE_TOKEN, r.token);
        },
        async sair() {
            try {
                await requisitar('POST', '/auth/logout');
            } finally {
                sessionStorage.removeItem(CHAVE_TOKEN);
            }
        },
        eu: () => requisitar('GET', '/auth/eu'),

        catalogo: () => requisitar('GET', '/recursos'),
        salasDisponiveis: (p) => requisitar('GET', comParametros('/salas/disponiveis', p)),
        professoresDisponiveis: (p) => requisitar('GET', comParametros('/professores/disponiveis', p)),
        materiaisDisponiveis: (p) => requisitar('GET', comParametros('/materiais/disponiveis', p)),

        reservas: () => requisitar('GET', '/reservas'),
        criarReserva: (corpo) => requisitar('POST', '/reservas', corpo),
        alterarReserva: (reservaId, inicio, fim) => requisitar('PUT', '/reservas/' + id(reservaId), { inicio, fim }),
        cancelarReserva: (reservaId, motivo) => requisitar('POST', '/reservas/' + id(reservaId) + '/cancelamento', { motivo }),
        aprovarReserva: (reservaId) => requisitar('POST', '/reservas/' + id(reservaId) + '/aprovacao'),
        rejeitarReserva: (reservaId, justificativa) =>
            requisitar('POST', '/reservas/' + id(reservaId) + '/rejeicao', { justificativa }),
        iniciarUso: (reservaId) => requisitar('POST', '/reservas/' + id(reservaId) + '/inicio-uso'),
        concluirReserva: (reservaId) => requisitar('POST', '/reservas/' + id(reservaId) + '/conclusao'),
        registrarNaoComparecimento: (reservaId) => requisitar('POST', '/reservas/' + id(reservaId) + '/nao-comparecimento'),
        historico: (reservaId) => requisitar('GET', '/reservas/' + id(reservaId) + '/historico'),

        retiradas: (reservaId) => requisitar('GET', '/reservas/' + id(reservaId) + '/retiradas'),
        registrarRetirada: (reservaId, materialId) =>
            requisitar('POST', '/reservas/' + id(reservaId) + '/retiradas', { materialId }),
        registrarDevolucao: (retiradaId, condicao) =>
            requisitar('POST', '/retiradas/' + id(retiradaId) + '/devolucao', { condicao }),

        relatorio: (p) => requisitar('GET', comParametros('/relatorios/utilizacao', p)),

        cadastrarSala: (corpo) => requisitar('POST', '/salas', corpo),
        cadastrarProfessor: (corpo) => requisitar('POST', '/professores', corpo),
        cadastrarMaterial: (corpo) => requisitar('POST', '/materiais', corpo),
        inativar: (tipo, recursoId) => {
            const base = { SALA: '/salas/', PROFESSOR: '/professores/', MATERIAL: '/materiais/' }[tipo];
            return requisitar('POST', base + id(recursoId) + '/inativacao');
        },
        editar: (tipo, recursoId, corpo) => {
            const base = { SALA: '/salas/', PROFESSOR: '/professores/', MATERIAL: '/materiais/' }[tipo];
            return requisitar('PUT', base + id(recursoId), corpo);
        },
        cadastrarManutencao: (corpo) => requisitar('POST', '/manutencoes', corpo),
        manutencoes: () => requisitar('GET', '/manutencoes'),
        encerrarManutencao: (manutencaoId) => requisitar('POST', '/manutencoes/' + id(manutencaoId) + '/encerramento'),
        retiradasPendentes: () => requisitar('GET', '/retiradas/pendentes'),

        usuarios: () => requisitar('GET', '/usuarios'),
        cadastrarUsuario: (corpo) => requisitar('POST', '/usuarios', corpo),
        inativarUsuario: (usuarioId) => requisitar('POST', '/usuarios/' + id(usuarioId) + '/inativacao'),
        auditoria: (p) => requisitar('GET', comParametros('/auditoria', p)),
    };
})();
