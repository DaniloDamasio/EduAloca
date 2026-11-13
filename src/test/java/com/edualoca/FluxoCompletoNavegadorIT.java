package com.edualoca;

import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.notificacao.StatusNotificacao;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end em navegador real (Chromium headless, Playwright) sobre a aplicacao inteira em porta aleatoria: interface
 * em {@code static/}, API, dominio e notificacao. Nenhum atalho por codigo: cada passo e um clique ou digitacao na tela.
 *
 * <p>Fluxo: Administrador cadastra sala restrita e material; Solicitante busca, monta e pede a reserva; Responsavel do
 * Bloco A aprova, inicia o uso, registra retirada e devolucao do material e conclui; Solicitante ve a reserva concluida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FluxoCompletoNavegadorIT {

    private static Playwright playwright;
    private static Browser navegador;

    @LocalServerPort
    private int porta;

    private BrowserContext contexto;
    private Page pagina;

    @BeforeAll
    static void abrirNavegador() {
        playwright = Playwright.create();
        navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void fecharNavegador() {
        if (navegador != null) {
            navegador.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void novaSessao() {
        Banco.resetParaTeste();
    }

    @AfterEach
    void fecharSessao() {
        if (contexto != null) {
            contexto.close();
        }
    }

    private void abrir(int largura, int altura) {
        contexto = navegador.newContext(new Browser.NewContextOptions().setViewportSize(largura, altura).setLocale("pt-BR"));
        pagina = contexto.newPage();
        pagina.navigate("http://localhost:" + porta + "/");
    }

    private void entrarComo(String botaoDemo) {
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(botaoDemo).setExact(true)).click();
        assertThat(pagina.locator("#tela-app")).isVisible();
    }

    private void sair() {
        pagina.locator("#sair").click();
        assertThat(pagina.locator("#tela-login")).isVisible();
    }

    private void aba(String nome) {
        pagina.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(nome)).click();
    }

    private void avisoContem(String texto) {
        assertThat(pagina.locator("#aviso")).containsText(texto);
    }

    private Locator cartaoDaReserva(String finalidade) {
        return pagina.locator("article.reserva").filter(new Locator.FilterOptions().setHasText(finalidade));
    }

    private void clicarNoCartao(String finalidade, String botao) {
        cartaoDaReserva(finalidade).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(botao).setExact(true)).click();
    }

    @Test
    @DisplayName("E2E: cadastro -> busca e reserva restrita -> aprovacao -> inicio de uso -> retirada/devolucao -> conclusao")
    void fluxoCompletoDosTresPerfis() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String sala = "Lab E2E " + sufixo;
        String material = "Projetor E2E " + sufixo;
        String tipoMaterial = "projetor-" + sufixo;
        String finalidade = "Aula E2E " + sufixo;
        abrir(1280, 900);

        // Administrador: cadastra sala restrita no Bloco A e um material.
        entrarComo("Administrador");
        aba("Cadastro e manutenção");
        Locator formSala = pagina.locator("#form-sala");
        formSala.locator("[name=nome]").fill(sala);
        formSala.locator("[name=tipo]").fill("laboratorio");
        formSala.locator("[name=capacidade]").fill("20");
        formSala.locator("[name=localizacao]").selectOption("Bloco A");
        formSala.locator("[name=restrito]").check();
        formSala.locator("button[type=submit]").click();
        avisoContem("Sala cadastrada");
        // Edicao pela tela (RF-04): capacidade 20 -> 25, refletida no catalogo.
        Locator itemSala = pagina.locator("#catalogo li").filter(new Locator.FilterOptions().setHasText(sala));
        itemSala.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Editar")).click();
        pagina.locator("#dialogo [name=capacidade]").fill("25");
        pagina.locator("#dialogo-confirmar").click();
        avisoContem("Cadastro atualizado");
        assertThat(itemSala).containsText("25 lugares");
        Locator formMaterial = pagina.locator("#form-material");
        formMaterial.locator("[name=nome]").fill(material);
        formMaterial.locator("[name=tipo]").fill(tipoMaterial);
        formMaterial.locator("[name=quantidadeTotal]").fill("1");
        formMaterial.locator("button[type=submit]").click();
        avisoContem("Material cadastrado");
        sair();

        // Solicitante: busca sala e material no mesmo horario, monta a reserva e pede.
        entrarComo("Solicitante");
        Locator busca = pagina.locator("#form-busca");
        busca.locator("[name=inicio]").fill("2027-10-04T08:00");
        busca.locator("[name=fim]").fill("2027-10-04T10:00");
        busca.locator("#busca-recurso").selectOption("SALA");
        busca.locator("[name=localizacao]").fill("Bloco A");
        busca.locator("button[type=submit]").click();
        pagina.locator("#resultado-busca li").filter(new Locator.FilterOptions().setHasText(sala))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Adicionar")).click();
        busca.locator("#busca-recurso").selectOption("MATERIAL");
        busca.locator("[name=tipoMaterial]").fill(tipoMaterial);
        busca.locator("button[type=submit]").click();
        pagina.locator("#resultado-busca li").filter(new Locator.FilterOptions().setHasText(material))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Adicionar")).click();
        assertThat(pagina.locator("#carrinho li")).hasCount(2);
        pagina.locator("#form-reserva [name=finalidade]").fill(finalidade);
        pagina.locator("#form-reserva button[type=submit]").click();
        avisoContem("aguardando aprovação do Responsável");
        aba("Minhas reservas");
        assertThat(cartaoDaReserva(finalidade).locator(".selo")).hasText("Solicitada");
        sair();

        // Responsavel do Bloco A: aprova, inicia o uso, retira e devolve o material, conclui.
        entrarComo("Responsável (Bloco A)");
        aba("Aprovações e uso");
        assertThat(pagina.locator("#filtro-estado")).hasValue("SOLICITADA");
        pagina.locator("#filtro-estado").selectOption("");
        clicarNoCartao(finalidade, "Aprovar");
        assertThat(cartaoDaReserva(finalidade).locator(".selo")).hasText("Aprovada");
        clicarNoCartao(finalidade, "Iniciar uso");
        assertThat(cartaoDaReserva(finalidade).locator(".selo")).hasText("Em uso");
        clicarNoCartao(finalidade, "Retirada e devolução");
        cartaoDaReserva(finalidade).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Registrar retirada")).click();
        avisoContem("Retirada registrada");
        cartaoDaReserva(finalidade).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Registrar devolução")).click();
        pagina.locator("#dialogo [name=condicao]").selectOption("INTEGRO");
        pagina.locator("#dialogo-confirmar").click();
        avisoContem("Devolução registrada");
        assertThat(cartaoDaReserva(finalidade)).containsText("Ciclos encerrados");
        clicarNoCartao(finalidade, "Concluir");
        assertThat(cartaoDaReserva(finalidade).locator(".selo")).hasText("Concluída");
        clicarNoCartao(finalidade, "Histórico");
        // Historico auditavel na tela: criacao + 3 transicoes, a ultima de "Em uso" para "Concluida" (RF-27).
        Locator linhas = cartaoDaReserva(finalidade).locator("table tbody tr");
        assertThat(linhas).hasCount(4);
        assertThat(linhas.last().locator("td").nth(1)).hasText("Em uso");
        assertThat(linhas.last().locator("td").nth(2)).hasText("Concluída");
        sair();

        // Solicitante: ve o resultado final.
        entrarComo("Solicitante");
        aba("Minhas reservas");
        assertThat(cartaoDaReserva(finalidade).locator(".selo")).hasText("Concluída");

        // Efeito colateral do fluxo real: Responsavel notificado da pendencia e Solicitante a cada transicao (RF-31/32).
        assertThat(Banco.notificacoes.values()).filteredOn(n -> n.getStatus() == StatusNotificacao.ENVIADA)
                .extracting(n -> n.getMensagem())
                .contains("Nova solicitação restrita pendente de aprovação", "Sua reserva mudou para APROVADA",
                        "Sua reserva mudou para EM_USO", "Sua reserva mudou para CONCLUIDA");
    }

    @Test
    @DisplayName("E2E (RF-13/RF-38): em 375 px nao ha rolagem horizontal, e entrada invalida mostra mensagem compreensivel")
    void celular_semRolagemHorizontal_eErroCompreensivel() {
        abrir(375, 740);
        assertThat((Boolean) pagina.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();

        entrarComo("Solicitante");
        assertThat((Boolean) pagina.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
        assertThat(pagina.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Buscar e reservar"))).isVisible();

        // Busca com fim antes do inicio: a API responde 400 e a tela mostra a mensagem em portugues, sem detalhe tecnico.
        Locator busca = pagina.locator("#form-busca");
        busca.locator("[name=inicio]").fill("2027-11-08T16:00");
        busca.locator("[name=fim]").fill("2027-11-08T14:00");
        busca.locator("button[type=submit]").click();
        avisoContem("fim deve ser posterior ao início");
        assertThat(pagina.locator("#aviso")).not().containsText("Exception");
        assertThat((Boolean) pagina.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
    }

    private boolean semRolagemHorizontal() {
        return (Boolean) pagina.evaluate("document.documentElement.scrollWidth <= window.innerWidth");
    }

    @ParameterizedTest(name = "{0} x {1} px")
    @CsvSource({"360, 740", "1920, 1080"})
    @DisplayName("E2E (RF-37): nos extremos de 360 e 1920 px, nenhuma aba de nenhum perfil tem rolagem horizontal")
    void extremosDeViewport_todasAsAbasDosTresPerfis(int largura, int altura) {
        abrir(largura, altura);
        assertThat(semRolagemHorizontal()).as("login").isTrue();

        for (String conta : List.of("Solicitante", "Responsável (Bloco A)", "Administrador")) {
            entrarComo(conta);
            List<String> abas = pagina.locator(".aba").allTextContents();
            assertThat(abas).as("abas de %s", conta).isNotEmpty();
            for (String nome : abas) {
                aba(nome);
                assertThat(pagina.locator("[role=tab][aria-selected=true]")).hasText(nome);
                assertThat(semRolagemHorizontal()).as("%s, aba %s, %d px", conta, nome, largura).isTrue();
            }
            sair();
        }
    }

    @Test
    @DisplayName("E2E: Administrador confirma manutencao sobre reserva aprovada, encerra a manutencao, gerencia usuario e consulta a auditoria; Responsavel busca e ve o catalogo")
    void telasDoAdministradorEDoResponsavel() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String sala = "Lab Admin " + sufixo;
        String finalidade = "Aula Admin " + sufixo;
        String email = "resp." + sufixo + "@example.com";
        abrir(1280, 900);

        // Administrador cadastra uma sala comum.
        entrarComo("Administrador");
        aba("Cadastro e manutenção");
        Locator formSala = pagina.locator("#form-sala");
        formSala.locator("[name=nome]").fill(sala);
        formSala.locator("[name=tipo]").fill("laboratorio");
        formSala.locator("[name=capacidade]").fill("20");
        formSala.locator("[name=localizacao]").selectOption("Bloco A");
        formSala.locator("button[type=submit]").click();
        avisoContem("Sala cadastrada");
        sair();

        // Solicitante reserva a sala: nasce aprovada.
        entrarComo("Solicitante");
        Locator busca = pagina.locator("#form-busca");
        busca.locator("[name=inicio]").fill("2027-10-05T08:00");
        busca.locator("[name=fim]").fill("2027-10-05T10:00");
        busca.locator("#busca-recurso").selectOption("SALA");
        busca.locator("[name=localizacao]").fill("Bloco A");
        busca.locator("button[type=submit]").click();
        pagina.locator("#resultado-busca li").filter(new Locator.FilterOptions().setHasText(sala))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Adicionar")).click();
        pagina.locator("#form-reserva [name=finalidade]").fill(finalidade);
        pagina.locator("#form-reserva button[type=submit]").click();
        avisoContem("aprovada automaticamente");
        sair();

        // RF-23: manutencao sobre a reserva aprovada pede confirmacao; confirmar cancela a reserva e bloqueia a sala.
        entrarComo("Administrador");
        aba("Cadastro e manutenção");
        Locator formManutencao = pagina.locator("#form-manutencao");
        formManutencao.locator("#manutencao-recurso").selectOption(new SelectOption().setLabel("Sala · " + sala));
        formManutencao.locator("[name=inicio]").fill("2027-10-05T07:00");
        formManutencao.locator("[name=fim]").fill("2027-10-05T12:00");
        formManutencao.locator("[name=motivo]").fill("vazamento " + sufixo);
        formManutencao.locator("button[type=submit]").click();
        assertThat(pagina.locator("#dialogo-titulo")).hasText("Há reserva aprovada nesse período");
        pagina.locator("#dialogo [name=aviso]").selectOption("sim");
        pagina.locator("#dialogo-confirmar").click();
        avisoContem("Manutenção registrada");
        Locator linhaManutencao = pagina.locator("#lista-manutencoes tr").filter(new Locator.FilterOptions().setHasText(sala));
        assertThat(linhaManutencao).containsText("vazamento " + sufixo);

        // Encerrar a manutencao (RF-21) tira a linha da lista.
        linhaManutencao.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Encerrar")).click();
        pagina.locator("#dialogo-confirmar").click();
        avisoContem("Manutenção encerrada");
        assertThat(linhaManutencao).hasCount(0);

        // Usuarios: cadastra Responsavel do Bloco C e inativa a conta.
        aba("Usuários");
        Locator formUsuario = pagina.locator("#form-usuario");
        formUsuario.locator("[name=email]").fill(email);
        formUsuario.locator("[name=senha]").fill("senhaForte123");
        formUsuario.locator("#usuario-perfil-novo").selectOption("RESPONSAVEL");
        assertThat(pagina.locator("#usuario-local")).isVisible();
        formUsuario.locator("[name=localizacao]").fill("Bloco C");
        formUsuario.locator("button[type=submit]").click();
        avisoContem("Usuário cadastrado");
        Locator conta = pagina.locator("#lista-usuarios li").filter(new Locator.FilterOptions().setHasText(email));
        assertThat(conta).containsText("Responde por Bloco C");
        conta.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Inativar")).click();
        pagina.locator("#dialogo-confirmar").click();
        avisoContem("Conta inativada");
        assertThat(conta).containsText("inativa");

        // Auditoria (RF-28): eventos de usuario com o Administrador como autor.
        aba("Auditoria");
        pagina.locator("#form-auditoria [name=entidade]").selectOption("Usuario");
        pagina.locator("#form-auditoria button[type=submit]").click();
        Locator auditoria = pagina.locator("#resultado-auditoria");
        assertThat(auditoria).containsText("INATIVACAO");
        assertThat(auditoria).containsText("CRIACAO");
        assertThat(auditoria).containsText("admin@example.com");
        sair();

        // Solicitante ve a reserva cancelada pela manutencao.
        entrarComo("Solicitante");
        aba("Minhas reservas");
        pagina.locator("#filtro-estado").selectOption("");
        assertThat(cartaoDaReserva(finalidade).locator(".selo")).hasText("Cancelada");
        sair();

        // Responsavel: busca disponivel (sem montar reserva) e catalogo somente leitura (RF-07 a RF-10).
        entrarComo("Responsável (Bloco A)");
        aba("Buscar disponibilidade");
        assertThat(pagina.locator("#cartao-nova-reserva")).isHidden();
        Locator buscaResp = pagina.locator("#form-busca");
        buscaResp.locator("[name=inicio]").fill("2027-10-06T08:00");
        buscaResp.locator("[name=fim]").fill("2027-10-06T10:00");
        buscaResp.locator("button[type=submit]").click();
        Locator resultado = pagina.locator("#resultado-busca li").filter(new Locator.FilterOptions().setHasText(sala));
        assertThat(resultado).hasCount(1);
        assertThat(resultado.getByRole(AriaRole.BUTTON)).hasCount(0);
        aba("Catálogo");
        assertThat(pagina.locator("#catalogo-consulta")).containsText(sala);
        assertThat(pagina.locator("#catalogo-consulta button")).hasCount(0);
    }
}
