/*
 * Telas do EduAloca. Todo dado vindo da API entra no DOM por textContent (funcao el), nunca por innerHTML,
 * para que nomes e textos digitados por usuarios nao sejam interpretados como HTML.
 */
'use strict';

// ---------------------------------------------------------------------------------------------------------------
// Utilitarios
// ---------------------------------------------------------------------------------------------------------------

const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
const $$ = (seletor, raiz = document) => Array.from(raiz.querySelectorAll(seletor));

/** Cria elemento: props aceita class, text, on* (eventos) e atributos comuns; filhos podem ser texto ou nos. */
function el(tag, props = {}, ...filhos) {
    const no = document.createElement(tag);
    for (const [chave, valor] of Object.entries(props)) {
        if (valor === undefined || valor === null || valor === false) continue;
        if (chave === 'class') no.className = valor;
        else if (chave === 'text') no.textContent = valor;
        else if (chave.startsWith('on')) no.addEventListener(chave.slice(2), valor);
        else no.setAttribute(chave, valor === true ? '' : String(valor));
    }
    for (const filho of filhos.flat()) {
        if (filho === undefined || filho === null || filho === false) continue;
        no.append(filho instanceof Node ? filho : document.createTextNode(String(filho)));
    }
    return no;
}

const ROTULO_PERFIL = { SOLICITANTE: 'Solicitante', RESPONSAVEL: 'Responsável', ADMINISTRADOR: 'Administrador' };
const ROTULO_ESTADO = {
    SOLICITADA: 'Solicitada', APROVADA: 'Aprovada', EM_USO: 'Em uso', CONCLUIDA: 'Concluída',
    REJEITADA: 'Rejeitada', CANCELADA: 'Cancelada', NAO_COMPARECEU: 'Não compareceu',
};
const ROTULO_TIPO = { SALA: 'Sala', PROFESSOR: 'Professor', MATERIAL: 'Material' };

const formatoData = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const formatarData = (iso) => (iso ? formatoData.format(new Date(iso)) : '—');
const formatarPeriodo = (inicio, fim) => formatarData(inicio) + ' → ' + formatarData(fim);
const idCurto = (uuid) => (uuid ? String(uuid).slice(0, 8) : '—');
/** Autor de um registro: "você" quando e o usuario logado; senao o inicio do id (a API nao expoe e-mail de terceiros). */
const autor = (uuid) => (estado.usuario && uuid === estado.usuario.id ? 'você' : idCurto(uuid));

/** Date -> valor de input datetime-local (aaaa-mm-ddThh:mm, horario local). */
function paraInput(data) {
    const p = (n) => String(n).padStart(2, '0');
    return data.getFullYear() + '-' + p(data.getMonth() + 1) + '-' + p(data.getDate()) + 'T' + p(data.getHours()) + ':' + p(data.getMinutes());
}

function preencherPeriodoPadrao(form, horaInicio, horaFim, diasAFrente = 1) {
    const base = new Date();
    base.setDate(base.getDate() + diasAFrente);
    base.setSeconds(0, 0);
    const inicio = new Date(base); inicio.setHours(horaInicio, 0);
    const fim = new Date(base); fim.setHours(horaFim, 0);
    form.elements.inicio.value = paraInput(inicio);
    form.elements.fim.value = paraInput(fim);
}

let temporizadorAviso;
function aviso(mensagem, tipo = 'info') {
    const caixa = $('#aviso');
    caixa.textContent = mensagem;
    caixa.className = 'aviso aviso--' + tipo;
    caixa.hidden = false;
    clearTimeout(temporizadorAviso);
    temporizadorAviso = setTimeout(() => { caixa.hidden = true; }, tipo === 'erro' ? 8000 : 5000);
}

/** Executa a acao com o botao desabilitado e mostra o erro da API, se houver. */
async function executar(botao, acao, mensagemOk) {
    if (botao) botao.disabled = true;
    try {
        const resultado = await acao();
        if (mensagemOk) aviso(typeof mensagemOk === 'function' ? mensagemOk(resultado) : mensagemOk, 'ok');
        return resultado;
    } catch (e) {
        aviso(e.message || 'Erro inesperado', 'erro');
        return undefined;
    } finally {
        if (botao) botao.disabled = false;
    }
}

/**
 * Dialogo modal simples. campos: [{ nome, rotulo, tipo: 'text'|'textarea'|'datetime-local'|'select', valor, opcoes }].
 * Resolve com { nome: valor } ou null se o usuario voltar.
 */
function dialogo(titulo, campos, textoConfirmar = 'Confirmar') {
    const caixa = $('#dialogo');
    const form = $('#dialogo-form');
    $('#dialogo-titulo').textContent = titulo;
    $('#dialogo-confirmar').textContent = textoConfirmar;
    const area = $('#dialogo-campos');
    area.replaceChildren(...campos.map((c) => {
        let entrada;
        if (c.tipo === 'textarea') {
            entrada = el('textarea', { name: c.nome, required: c.obrigatorio !== false, maxlength: 500 });
        } else if (c.tipo === 'select') {
            entrada = el('select', { name: c.nome }, c.opcoes.map(([valor, rotulo]) => el('option', { value: valor, text: rotulo })));
        } else {
            entrada = el('input', { name: c.nome, type: c.tipo || 'text', required: c.obrigatorio !== false, maxlength: 120 });
        }
        if (c.valor !== undefined) entrada.value = c.valor;
        return el('label', {}, c.rotulo, entrada);
    }));
    return new Promise((resolver) => {
        const fechar = (valor) => {
            form.removeEventListener('submit', confirmar);
            $('#dialogo-cancelar').removeEventListener('click', cancelar);
            caixa.removeEventListener('cancel', cancelar);
            caixa.close();
            resolver(valor);
        };
        const confirmar = (evento) => {
            evento.preventDefault();
            if (!form.reportValidity()) return;
            fechar(Object.fromEntries(campos.map((c) => [c.nome, form.elements[c.nome].value.trim()])));
        };
        const cancelar = (evento) => { if (evento) evento.preventDefault(); fechar(null); };
        form.addEventListener('submit', confirmar);
        $('#dialogo-cancelar').addEventListener('click', cancelar);
        caixa.addEventListener('cancel', cancelar);
        caixa.showModal();
        const primeiro = area.querySelector('input, textarea, select');
        if (primeiro) primeiro.focus();
    });
}

// ---------------------------------------------------------------------------------------------------------------
// Estado da sessao
// ---------------------------------------------------------------------------------------------------------------

const estado = {
    usuario: null,
    carrinho: [],          // [{ tipo, id, nome, quantidade }]
    periodoBusca: null,    // { inicio, fim } da ultima busca
    reservas: [],
    catalogo: { salas: [], professores: [], materiais: [] },
};

const perfil = () => estado.usuario && estado.usuario.perfil;
const ehDecisor = () => perfil() === 'RESPONSAVEL' || perfil() === 'ADMINISTRADOR';

// ---------------------------------------------------------------------------------------------------------------
// Login e montagem das telas por perfil
// ---------------------------------------------------------------------------------------------------------------

function mostrarLogin() {
    estado.usuario = null;
    $('#tela-login').hidden = false;
    $('#tela-app').hidden = true;
    $('#usuario').hidden = true;
}

async function iniciarSessao() {
    estado.usuario = await Api.eu();
    $('#usuario-perfil').textContent = ROTULO_PERFIL[perfil()] || perfil();
    $('#usuario-email').textContent = estado.usuario.email;
    $('#usuario').hidden = false;
    $('#tela-login').hidden = true;
    $('#tela-app').hidden = false;
    montarAbas();
    $('#titulo-reservas').textContent = perfil() === 'SOLICITANTE' ? 'Minhas reservas'
        : perfil() === 'RESPONSAVEL' ? 'Reservas sob sua responsabilidade' : 'Todas as reservas';
    $('#filtro-estado').value = ehDecisor() ? 'SOLICITADA' : '';
    await Promise.all([carregarReservas(), carregarCatalogo()]);
}

function montarAbas() {
    const paineis = $$('.painel').filter((p) => p.dataset.perfis.split(' ').includes(perfil()));
    $$('.painel').forEach((p) => { p.hidden = true; });
    const abas = paineis.map((painel) => el('button', {
        type: 'button', class: 'aba', role: 'tab', 'aria-controls': painel.id,
        text: painel.id === 'painel-reservas' && perfil() !== 'SOLICITANTE' ? 'Aprovações e uso'
            : painel.id === 'painel-reservas' ? 'Minhas reservas' : painel.dataset.titulo,
        onclick: () => selecionarAba(painel.id),
    }));
    $('#abas').replaceChildren(...abas);
    if (paineis.length) selecionarAba(paineis[0].id);
}

function selecionarAba(idPainel) {
    $$('.aba').forEach((aba) => aba.setAttribute('aria-selected', String(aba.getAttribute('aria-controls') === idPainel)));
    $$('.painel').forEach((p) => { p.hidden = p.id !== idPainel; });
    if (idPainel === 'painel-reservas') carregarReservas();
}

// ---------------------------------------------------------------------------------------------------------------
// Solicitante: busca e carrinho
// ---------------------------------------------------------------------------------------------------------------

function trocarFiltroBusca() {
    const tipo = $('#busca-recurso').value;
    $$('#form-busca [data-filtro]').forEach((bloco) => { bloco.hidden = bloco.dataset.filtro !== tipo; });
}

async function buscar(evento) {
    evento.preventDefault();
    const form = evento.target;
    const f = form.elements;
    const periodo = { inicio: f.inicio.value, fim: f.fim.value };
    const tipo = f.recurso.value;
    const resultado = await executar(form.querySelector('button[type=submit]'), () => {
        if (tipo === 'SALA') {
            return Api.salasDisponiveis({ ...periodo, tipo: f.tipoSala.value, capacidadeMinima: f.capacidadeMinima.value, localizacao: f.localizacao.value });
        }
        if (tipo === 'PROFESSOR') {
            return Api.professoresDisponiveis({ ...periodo, competencia: f.competencia.value });
        }
        return Api.materiaisDisponiveis({ ...periodo, tipo: f.tipoMaterial.value, quantidade: f.quantidade.value });
    });
    if (!resultado) return;

    const mudouPeriodo = estado.periodoBusca
        && (estado.periodoBusca.inicio !== periodo.inicio || estado.periodoBusca.fim !== periodo.fim);
    if (mudouPeriodo && estado.carrinho.length) {
        estado.carrinho = [];
        aviso('O horário mudou: os recursos escolhidos antes foram retirados da nova reserva.');
    }
    estado.periodoBusca = periodo;
    const quantidade = tipo === 'MATERIAL' ? Math.max(1, Number(f.quantidade.value) || 1) : 1;
    renderizarResultadoBusca(tipo, resultado, quantidade);
    renderizarCarrinho();
}

function descreverRecurso(tipo, r) {
    if (tipo === 'SALA') return r.tipo + ' · ' + r.capacidade + ' lugares · ' + r.localizacao + (r.restrito ? ' · restrita' : '');
    if (tipo === 'PROFESSOR') return (r.competencias || []).join(', ') || 'sem competências';
    return r.tipo + ' · ' + r.quantidadeTotal + ' unidades' + (r.restrito ? ' · restrito' : '');
}

function renderizarResultadoBusca(tipo, itens, quantidade) {
    const lista = $('#resultado-busca');
    if (!itens.length) {
        lista.replaceChildren(el('li', { class: 'vazio', text: 'Nenhum recurso disponível com esses filtros.' }));
        return;
    }
    lista.replaceChildren(...itens.map((r) => el('li', {},
        el('div', {}, el('strong', { text: r.nome }), el('div', { class: 'dica', text: descreverRecurso(tipo, r) })),
        el('button', {
            type: 'button', class: 'botao botao--pequeno', text: 'Adicionar',
            onclick: () => adicionarAoCarrinho({ tipo, id: r.id, nome: r.nome, quantidade, restrito: !!r.restrito }),
        }))));
}

function adicionarAoCarrinho(item) {
    if (estado.carrinho.some((c) => c.tipo === item.tipo && c.id === item.id)) {
        aviso('Esse recurso já está na reserva.');
        return;
    }
    estado.carrinho.push(item);
    renderizarCarrinho();
}

function renderizarCarrinho() {
    const lista = $('#carrinho');
    $('#carrinho-horario').textContent = estado.periodoBusca
        ? 'Horário: ' + formatarPeriodo(estado.periodoBusca.inicio, estado.periodoBusca.fim) : '';
    if (!estado.carrinho.length) {
        lista.replaceChildren(el('li', { class: 'vazio', text: 'Nenhum recurso escolhido.' }));
        return;
    }
    lista.replaceChildren(...estado.carrinho.map((item, indice) => el('li', {},
        el('span', {}, el('span', { class: 'selo', text: ROTULO_TIPO[item.tipo] }), ' ', item.nome,
            item.tipo === 'MATERIAL' ? ' × ' + item.quantidade : '', item.restrito ? ' (exige aprovação)' : ''),
        el('button', {
            type: 'button', class: 'botao botao--perigo botao--pequeno', text: 'Remover',
            onclick: () => { estado.carrinho.splice(indice, 1); renderizarCarrinho(); },
        }))));
}

async function criarReserva(evento) {
    evento.preventDefault();
    const form = evento.target;
    if (!estado.periodoBusca || !estado.carrinho.length) {
        aviso('Busque e adicione ao menos um recurso antes de solicitar a reserva.', 'erro');
        return;
    }
    const corpo = {
        inicio: estado.periodoBusca.inicio,
        fim: estado.periodoBusca.fim,
        finalidade: form.elements.finalidade.value.trim(),
        recursos: estado.carrinho.map(({ tipo, id, quantidade }) => ({ tipo, id, quantidade })),
    };
    const reserva = await executar(form.querySelector('button[type=submit]'), () => Api.criarReserva(corpo),
        (r) => r.estado === 'SOLICITADA'
            ? 'Reserva criada e aguardando aprovação do Responsável (recurso restrito).'
            : 'Reserva criada e aprovada automaticamente.');
    if (!reserva) return;
    estado.carrinho = [];
    form.reset();
    renderizarCarrinho();
    $('#resultado-busca').replaceChildren();
}

// ---------------------------------------------------------------------------------------------------------------
// Reservas (todos os perfis)
// ---------------------------------------------------------------------------------------------------------------

async function carregarReservas() {
    const reservas = await executar(null, () => Api.reservas());
    if (!reservas) return;
    estado.reservas = reservas;
    renderizarReservas();
}

function renderizarReservas() {
    const filtro = $('#filtro-estado').value;
    const visiveis = estado.reservas.filter((r) => !filtro || r.estado === filtro);
    const area = $('#lista-reservas');
    if (!visiveis.length) {
        area.replaceChildren(el('p', { class: 'dica', text: filtro ? 'Nenhuma reserva nesse estado.' : 'Nenhuma reserva ainda.' }));
        return;
    }
    area.replaceChildren(...visiveis.map(cartaoReserva));
}

function cartaoReserva(r) {
    const extra = el('div', { class: 'reserva__extra', hidden: true });
    const acoes = el('div', { class: 'reserva__acoes' });
    const botao = (texto, classe, acao) => el('button', { type: 'button', class: 'botao botao--pequeno ' + classe, text: texto, onclick: acao });

    if (perfil() === 'SOLICITANTE' && (r.estado === 'SOLICITADA' || r.estado === 'APROVADA')) {
        acoes.append(
            botao('Alterar horário', 'botao--secundario', (e) => alterarHorario(e.target, r)),
            botao('Cancelar', 'botao--perigo', (e) => cancelar(e.target, r)));
    }
    if (ehDecisor()) {
        if (r.estado === 'SOLICITADA') {
            acoes.append(
                botao('Aprovar', '', (e) => transicao(e.target, () => Api.aprovarReserva(r.id), 'Reserva aprovada.')),
                botao('Rejeitar', 'botao--perigo', (e) => rejeitar(e.target, r)));
        }
        if (r.estado === 'APROVADA') {
            acoes.append(
                botao('Iniciar uso', '', (e) => transicao(e.target, () => Api.iniciarUso(r.id), 'Uso da reserva iniciado.')),
                botao('Não compareceu', 'botao--perigo', (e) => transicao(e.target, () => Api.registrarNaoComparecimento(r.id), 'Não comparecimento registrado.')));
        }
        if (r.estado === 'EM_USO') {
            acoes.append(botao('Concluir', '', (e) => transicao(e.target, () => Api.concluirReserva(r.id), 'Reserva concluída.')));
        }
        if (r.estado === 'EM_USO' && r.recursos.some((x) => x.tipo === 'MATERIAL')) {
            acoes.append(botao('Retirada e devolução', 'botao--secundario', () => alternarExtra(extra, 'retiradas', () => painelRetiradas(extra, r))));
        }
        acoes.append(botao('Histórico', 'botao--secundario', () => alternarExtra(extra, 'historico', () => painelHistorico(extra, r))));
    }

    return el('article', { class: 'reserva reserva--' + r.estado },
        el('div', { class: 'reserva__topo' },
            el('span', { class: 'reserva__titulo', text: r.finalidade }),
            el('span', { class: 'selo selo--' + r.estado, text: ROTULO_ESTADO[r.estado] || r.estado })),
        el('p', { class: 'reserva__meta', text: formatarPeriodo(r.inicio, r.fim) + (ehDecisor() ? ' · solicitante ' + idCurto(r.solicitanteId) : '') }),
        el('p', { class: 'reserva__meta', text: r.recursos.map((x) => ROTULO_TIPO[x.tipo] + ': ' + x.nome + (x.tipo === 'MATERIAL' ? ' × ' + x.quantidade : '')).join(' · ') }),
        r.justificativaRejeicao ? el('p', { class: 'reserva__meta', text: 'Justificativa da rejeição: ' + r.justificativaRejeicao }) : null,
        acoes, extra);
}

async function transicao(botao, acao, mensagem) {
    if (await executar(botao, acao, mensagem)) await carregarReservas();
}

async function alterarHorario(botao, r) {
    const dados = await dialogo('Alterar horário', [
        { nome: 'inicio', rotulo: 'Novo início', tipo: 'datetime-local', valor: r.inicio.slice(0, 16) },
        { nome: 'fim', rotulo: 'Novo fim', tipo: 'datetime-local', valor: r.fim.slice(0, 16) },
    ], 'Alterar');
    if (dados) await transicao(botao, () => Api.alterarReserva(r.id, dados.inicio, dados.fim), 'Horário alterado.');
}

async function cancelar(botao, r) {
    const dados = await dialogo('Cancelar reserva', [{ nome: 'motivo', rotulo: 'Motivo do cancelamento', tipo: 'textarea' }], 'Cancelar reserva');
    if (dados) await transicao(botao, () => Api.cancelarReserva(r.id, dados.motivo), 'Reserva cancelada.');
}

async function rejeitar(botao, r) {
    const dados = await dialogo('Rejeitar solicitação', [{ nome: 'justificativa', rotulo: 'Justificativa (visível ao solicitante)', tipo: 'textarea' }], 'Rejeitar');
    if (dados) await transicao(botao, () => Api.rejeitarReserva(r.id, dados.justificativa), 'Solicitação rejeitada.');
}

/** Abre o painel extra com o conteudo pedido; clicar de novo no mesmo botao fecha, em outro troca o conteudo. */
function alternarExtra(extra, conteudo, preencher) {
    if (!extra.hidden && extra.dataset.conteudo === conteudo) {
        extra.hidden = true;
        return;
    }
    extra.dataset.conteudo = conteudo;
    extra.hidden = false;
    preencher();
}

async function painelHistorico(extra, r) {
    extra.replaceChildren(el('p', { class: 'dica', text: 'Carregando histórico…' }));
    const registros = await executar(null, () => Api.historico(r.id));
    if (!registros) { extra.hidden = true; return; }
    extra.replaceChildren(el('h3', { text: 'Histórico auditável' }), el('div', { class: 'tabela-rolagem' },
        el('table', {},
            el('thead', {}, el('tr', {}, ['Quando', 'De', 'Para', 'Autor', 'Motivo'].map((t) => el('th', { text: t })))),
            el('tbody', {}, registros.map((h) => el('tr', {},
                el('td', { text: formatarData(h.instante) }),
                el('td', { text: ROTULO_ESTADO[h.estadoAnterior] || '—' }),
                el('td', { text: ROTULO_ESTADO[h.estadoNovo] || h.estadoNovo || '—' }),
                el('td', { text: autor(h.usuarioId) }),
                el('td', { text: h.motivo || '' })))))));
}

async function painelRetiradas(extra, r) {
    const registros = await executar(null, () => Api.retiradas(r.id));
    if (!registros) { extra.hidden = true; return; }
    const materiais = r.recursos.filter((x) => x.tipo === 'MATERIAL');
    const linhas = materiais.map((m) => {
        const aberta = registros.find((x) => x.materialId === m.id && !x.dataDevolucao);
        const acao = aberta
            ? el('button', { type: 'button', class: 'botao botao--pequeno botao--secundario', text: 'Registrar devolução', onclick: (e) => devolver(e.target, aberta, extra, r) })
            : el('button', { type: 'button', class: 'botao botao--pequeno', text: 'Registrar retirada', onclick: (e) => retirar(e.target, m, extra, r) });
        return el('li', {}, el('span', {}, m.nome, aberta ? ' · retirado em ' + formatarData(aberta.dataRetirada) : ' · disponível para retirada'), acao);
    });
    const historico = registros.filter((x) => x.dataDevolucao).map((x) => {
        const material = materiais.find((m) => m.id === x.materialId);
        return el('li', {}, el('span', {}, (material ? material.nome : idCurto(x.materialId)) + ' · ' + formatarData(x.dataRetirada)
            + ' → ' + formatarData(x.dataDevolucao) + ' · ' + (x.condicaoDevolucao === 'DANIFICADO' ? 'danificado' : 'íntegro')));
    });
    const partes = [el('h3', { text: 'Retirada e devolução de material' }), el('ul', { class: 'lista' }, linhas)];
    if (historico.length) partes.push(el('h3', { text: 'Ciclos encerrados' }), el('ul', { class: 'lista' }, historico));
    extra.replaceChildren(...partes);
}

async function retirar(botao, material, extra, r) {
    if (await executar(botao, () => Api.registrarRetirada(r.id, material.id), 'Retirada registrada.')) painelRetiradas(extra, r);
}

async function devolver(botao, registro, extra, r) {
    const dados = await dialogo('Registrar devolução', [
        { nome: 'condicao', rotulo: 'Condição do item', tipo: 'select', opcoes: [['INTEGRO', 'Íntegro'], ['DANIFICADO', 'Danificado']] },
    ], 'Registrar');
    if (dados && await executar(botao, () => Api.registrarDevolucao(registro.id, dados.condicao), 'Devolução registrada.')) {
        painelRetiradas(extra, r);
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Relatorio (Responsavel e Administrador)
// ---------------------------------------------------------------------------------------------------------------

async function gerarRelatorio(evento) {
    evento.preventDefault();
    const form = evento.target;
    const dados = await executar(form.querySelector('button[type=submit]'),
        () => Api.relatorio({ inicio: form.elements.inicio.value, fim: form.elements.fim.value }));
    if (!dados) return;
    const horas = (n) => Number(n).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' h';
    const tabela = (titulo, cabecalho, linhas) => [
        el('h3', { text: titulo }),
        linhas.length
            ? el('div', { class: 'tabela-rolagem' }, el('table', {},
                el('thead', {}, el('tr', {}, cabecalho.map((t) => el('th', { text: t })))),
                el('tbody', {}, linhas.map((l) => el('tr', {}, l.map((c) => el('td', { text: c })))))))
            : el('p', { class: 'dica', text: 'Sem uso no período.' }),
    ];
    $('#resultado-relatorio').replaceChildren(
        ...tabela('Utilização por recurso', ['Tipo', 'Recurso', 'Horas reservadas'],
            dados.recursos.map((r) => [ROTULO_TIPO[r.tipo] || r.tipo, r.nome, horas(r.horasReservadas)])),
        ...tabela('Carga horária por professor', ['Professor', 'Carga alocada'],
            dados.professores.map((p) => [p.nome, horas(p.cargaHorariaAlocada)])),
        el('p', { class: 'destaque', text: 'Conflitos evitados no período: ' + dados.conflitosEvitados }),
        el('p', { class: 'dica', text: 'Conflitos contam pelo instante da tentativa recusada; reservas contam pelo horário reservado.' }));
}

// ---------------------------------------------------------------------------------------------------------------
// Cadastro (Administrador)
// ---------------------------------------------------------------------------------------------------------------

async function carregarCatalogo() {
    const catalogo = await executar(null, () => Api.catalogo());
    if (!catalogo) return;
    estado.catalogo = catalogo;
    if (perfil() === 'ADMINISTRADOR') renderizarCatalogo();
}

function itensDoCatalogo() {
    const c = estado.catalogo;
    return [
        ...c.salas.map((r) => ({ tipo: 'SALA', id: r.id, nome: r.nome, detalhe: descreverRecurso('SALA', r), dados: r })),
        ...c.professores.map((r) => ({ tipo: 'PROFESSOR', id: r.id, nome: r.nome, detalhe: descreverRecurso('PROFESSOR', r), dados: r })),
        ...c.materiais.map((r) => ({ tipo: 'MATERIAL', id: r.id, nome: r.nome, detalhe: descreverRecurso('MATERIAL', r), dados: r })),
    ];
}

function renderizarCatalogo() {
    const itens = itensDoCatalogo();
    $('#manutencao-recurso').replaceChildren(...itens.map((i) => el('option', { value: i.tipo + '|' + i.id, text: ROTULO_TIPO[i.tipo] + ' · ' + i.nome })));
    $('#catalogo').replaceChildren(el('ul', { class: 'lista' }, itens.map((i) => el('li', {},
        el('div', {}, el('span', { class: 'selo', text: ROTULO_TIPO[i.tipo] }), ' ', el('strong', { text: i.nome }), el('div', { class: 'dica', text: i.detalhe })),
        el('div', { class: 'reserva__acoes' },
            el('button', { type: 'button', class: 'botao botao--secundario botao--pequeno', text: 'Editar', onclick: (e) => editar(e.target, i) }),
            el('button', { type: 'button', class: 'botao botao--perigo botao--pequeno', text: 'Inativar', onclick: (e) => inativar(e.target, i) }))))));
}

/** RF-04/05/06 (DEF-015): edicao pelo Administrador; a validacao e a mesma do cadastro, no servidor. */
async function editar(botao, item) {
    const r = item.dados;
    const simNao = (valor) => ({ nome: 'restrito', rotulo: 'Exige aprovação', tipo: 'select', opcoes: [['false', 'Não'], ['true', 'Sim']], valor: String(valor) });
    const campos = {
        SALA: [{ nome: 'nome', rotulo: 'Nome', valor: r.nome }, { nome: 'tipo', rotulo: 'Tipo', valor: r.tipo },
            { nome: 'capacidade', rotulo: 'Capacidade', tipo: 'number', valor: r.capacidade },
            { nome: 'localizacao', rotulo: 'Localização', valor: r.localizacao }, simNao(r.restrito)],
        PROFESSOR: [{ nome: 'nome', rotulo: 'Nome', valor: r.nome },
            { nome: 'competencias', rotulo: 'Competências (separadas por vírgula)', valor: (r.competencias || []).join(', '), obrigatorio: false }],
        MATERIAL: [{ nome: 'nome', rotulo: 'Nome', valor: r.nome }, { nome: 'tipo', rotulo: 'Tipo', valor: r.tipo },
            { nome: 'quantidadeTotal', rotulo: 'Quantidade total', tipo: 'number', valor: r.quantidadeTotal }, simNao(r.restrito)],
    }[item.tipo];
    const dados = await dialogo('Editar ' + ROTULO_TIPO[item.tipo].toLowerCase() + ' "' + item.nome + '"', campos, 'Salvar');
    if (!dados) return;
    const corpo = {
        SALA: () => ({ nome: dados.nome, tipo: dados.tipo, capacidade: Number(dados.capacidade), localizacao: dados.localizacao, restrito: dados.restrito === 'true' }),
        PROFESSOR: () => ({ nome: dados.nome, competencias: dados.competencias.split(',').map((c) => c.trim()).filter(Boolean) }),
        MATERIAL: () => ({ nome: dados.nome, tipo: dados.tipo, quantidadeTotal: Number(dados.quantidadeTotal), restrito: dados.restrito === 'true' }),
    }[item.tipo]();
    if (await executar(botao, () => Api.editar(item.tipo, item.id, corpo), 'Cadastro atualizado.')) await carregarCatalogo();
}

async function inativar(botao, item) {
    const ok = await dialogo('Inativar ' + ROTULO_TIPO[item.tipo].toLowerCase() + ' "' + item.nome + '"?', [], 'Inativar');
    if (ok && await executar(botao, () => Api.inativar(item.tipo, item.id), 'Recurso inativado.') !== undefined) {
        await carregarCatalogo();
    }
}

function ligarCadastro(idForm, montarCorpo, chamar, mensagem) {
    $(idForm).addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const form = evento.target;
        const criado = await executar(form.querySelector('button[type=submit]'), () => chamar(montarCorpo(form.elements)), mensagem);
        if (criado) {
            form.reset();
            await carregarCatalogo();
        }
    });
}

async function registrarManutencao(evento) {
    evento.preventDefault();
    const f = evento.target.elements;
    const [tipoRecurso, recursoId] = f.recurso.value.split('|');
    const criada = await executar(evento.target.querySelector('button[type=submit]'),
        () => Api.cadastrarManutencao({ tipoRecurso, recursoId, inicio: f.inicio.value, fim: f.fim.value, motivo: f.motivo.value.trim() }),
        'Manutenção registrada: o recurso fica bloqueado no período.');
    if (criada) f.motivo.value = '';
}

// ---------------------------------------------------------------------------------------------------------------
// Inicializacao
// ---------------------------------------------------------------------------------------------------------------

document.addEventListener('DOMContentLoaded', async () => {
    Api.aoExpirar(() => { mostrarLogin(); aviso('Sua sessão expirou. Entre novamente.', 'erro'); });

    $('#form-login').addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const f = evento.target.elements;
        const ok = await executar(evento.target.querySelector('button[type=submit]'), async () => {
            await Api.entrar(f.email.value.trim(), f.senha.value);
            await iniciarSessao();
            return true;
        });
        if (ok) { evento.target.reset(); aviso('Bem-vindo(a)!', 'ok'); }
    });
    $$('[data-demo]').forEach((botao) => botao.addEventListener('click', () => {
        const [email, senha] = botao.dataset.demo.split('|');
        const f = $('#form-login').elements;
        f.email.value = email;
        f.senha.value = senha;
        $('#form-login').requestSubmit();
    }));
    $('#sair').addEventListener('click', async () => {
        await Api.sair().catch(() => {});
        estado.carrinho = [];
        estado.periodoBusca = null;
        mostrarLogin();
    });

    $('#busca-recurso').addEventListener('change', trocarFiltroBusca);
    $('#form-busca').addEventListener('submit', buscar);
    $('#form-reserva').addEventListener('submit', criarReserva);
    $('#filtro-estado').addEventListener('change', renderizarReservas);
    $('#atualizar-reservas').addEventListener('click', carregarReservas);
    $('#form-relatorio').addEventListener('submit', gerarRelatorio);
    $('#form-manutencao').addEventListener('submit', registrarManutencao);

    ligarCadastro('#form-sala', (f) => ({
        nome: f.nome.value, tipo: f.tipo.value, capacidade: Number(f.capacidade.value),
        localizacao: f.localizacao.value, restrito: f.restrito.checked,
    }), Api.cadastrarSala, 'Sala cadastrada.');
    ligarCadastro('#form-professor', (f) => ({
        nome: f.nome.value, identificadorInstitucional: f.identificadorInstitucional.value,
        competencias: f.competencias.value.split(',').map((c) => c.trim()).filter(Boolean),
    }), Api.cadastrarProfessor, 'Professor cadastrado.');
    ligarCadastro('#form-material', (f) => ({
        nome: f.nome.value, tipo: f.tipo.value, quantidadeTotal: Number(f.quantidadeTotal.value), restrito: f.restrito.checked,
    }), Api.cadastrarMaterial, 'Material cadastrado.');

    preencherPeriodoPadrao($('#form-busca'), 8, 10);
    preencherPeriodoPadrao($('#form-manutencao'), 13, 17);
    const relatorio = $('#form-relatorio');
    const hoje = new Date();
    relatorio.elements.inicio.value = paraInput(new Date(hoje.getFullYear(), hoje.getMonth(), 1));
    relatorio.elements.fim.value = paraInput(new Date(hoje.getFullYear(), hoje.getMonth() + 2, 1));
    renderizarCarrinho();

    if (Api.token()) {
        try { await iniciarSessao(); } catch (e) { mostrarLogin(); }
    } else {
        mostrarLogin();
    }
});
