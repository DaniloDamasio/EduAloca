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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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
                .contains("Nova solicitacao restrita pendente de aprovacao", "Sua reserva mudou para APROVADA",
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
        avisoContem("fim deve ser posterior ao inicio");
        assertThat(pagina.locator("#aviso")).not().containsText("Exception");
        assertThat((Boolean) pagina.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
    }
}
