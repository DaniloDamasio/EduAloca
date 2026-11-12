package com.edualoca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF-39: 100% dos endpoints publicos documentados. Compara as rotas registradas no Spring MVC (controllers de
 * {@code com.edualoca}) com as operacoes de {@code static/openapi.yaml}, nos dois sentidos: rota sem documentacao e
 * documentacao de rota inexistente falham. Tambem exige resumo e pelo menos uma resposta por operacao.
 */
@SpringBootTest
class DocumentacaoOpenApiTest {

    private static final Set<String> METODOS_DOCUMENTAVEIS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    @Autowired
    private WebApplicationContext contexto;

    private static Map<String, Object> documento() throws IOException {
        try (InputStream entrada = DocumentacaoOpenApiTest.class.getResourceAsStream("/static/openapi.yaml")) {
            assertThat(entrada).as("static/openapi.yaml no classpath").isNotNull();
            return new Yaml().load(entrada);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> caminhos() throws IOException {
        return (Map<String, Map<String, Object>>) documento().get("paths");
    }

    private Set<String> rotasDaAplicacao() {
        Set<String> rotas = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeamentos.getHandlerMethods().entrySet()) {
            if (!entrada.getValue().getBeanType().getPackageName().startsWith("com.edualoca")) {
                continue;
            }
            for (String caminho : entrada.getKey().getPatternValues()) {
                for (RequestMethod metodo : entrada.getKey().getMethodsCondition().getMethods()) {
                    rotas.add(metodo.name() + " " + caminho);
                }
            }
        }
        return rotas;
    }

    private static Set<String> rotasDocumentadas() throws IOException {
        Set<String> rotas = new TreeSet<>();
        caminhos().forEach((caminho, operacoes) -> operacoes.keySet().stream()
                .filter(METODOS_DOCUMENTAVEIS::contains)
                .forEach(metodo -> rotas.add(metodo.toUpperCase(Locale.ROOT) + " " + caminho)));
        return rotas;
    }

    @Test
    @DisplayName("Toda rota da aplicacao esta no openapi.yaml, e todo caminho do openapi.yaml existe na aplicacao")
    void documentacaoIgualAsRotas() throws IOException {
        Set<String> aplicacao = rotasDaAplicacao();
        Set<String> documentadas = rotasDocumentadas();

        assertThat(aplicacao).as("ha rotas na aplicacao").isNotEmpty();
        assertThat(documentadas).as("rotas sem documentacao: %s", diferenca(aplicacao, documentadas))
                .containsAll(aplicacao);
        assertThat(aplicacao).as("documentacao de rota inexistente: %s", diferenca(documentadas, aplicacao))
                .containsAll(documentadas);
    }

    @Test
    @DisplayName("Cada operacao tem resumo e ao menos uma resposta documentada")
    @SuppressWarnings("unchecked")
    void operacoesComResumoERespostas() throws IOException {
        caminhos().forEach((caminho, operacoes) -> operacoes.forEach((metodo, corpo) -> {
            if (!METODOS_DOCUMENTAVEIS.contains(metodo)) {
                return;
            }
            Map<String, Object> operacao = (Map<String, Object>) corpo;
            assertThat((String) operacao.get("summary")).as("summary de %s %s", metodo, caminho).isNotBlank();
            assertThat((Map<String, Object>) operacao.get("responses")).as("responses de %s %s", metodo, caminho).isNotEmpty();
        }));
    }

    @Test
    @DisplayName("O documento e servido pela aplicacao em /openapi.yaml, sem token")
    void documentoServido() throws Exception {
        MockMvcBuilders.webAppContextSetup(contexto).build()
                .perform(get("/openapi.yaml"))
                .andExpect(status().isOk());
    }

    private static Set<String> diferenca(Set<String> a, Set<String> b) {
        Set<String> resultado = new TreeSet<>(a);
        resultado.removeAll(b);
        return resultado;
    }
}
