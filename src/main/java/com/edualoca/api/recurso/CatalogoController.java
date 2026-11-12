package com.edualoca.api.recurso;

import com.edualoca.api.recurso.CadastroRequisicoes.MaterialRequisicao;
import com.edualoca.api.recurso.CadastroRequisicoes.ProfessorRequisicao;
import com.edualoca.api.recurso.CadastroRequisicoes.SalaRequisicao;
import com.edualoca.api.reserva.RecursoResolver;
import com.edualoca.api.seguranca.Perfis;
import com.edualoca.api.seguranca.UsuarioAutenticado;
import com.edualoca.domain.comum.Banco;
import com.edualoca.domain.comum.RegraNegocioException;
import com.edualoca.domain.recurso.CadastroRecursoService;
import com.edualoca.domain.recurso.Competencia;
import com.edualoca.domain.recurso.Material;
import com.edualoca.domain.recurso.PesquisaRecursoService;
import com.edualoca.domain.recurso.Professor;
import com.edualoca.domain.recurso.Sala;
import com.edualoca.domain.usuario.Perfil;
import com.edualoca.domain.usuario.Usuario;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Catalogo de salas, professores e materiais (RF-04 a RF-10). Qualquer usuario autenticado consulta; so o
 * Administrador cadastra e inativa. As regras de cada cadastro ficam em {@link CadastroRecursoService}; aqui so o
 * perfil e o tamanho da entrada (RNF-04).
 */
@RestController
public class CatalogoController {

    static final int MAX_TEXTO = 120;
    static final int MAX_COMPETENCIAS = 20;
    private static final String SO_ADMIN = "Apenas o Administrador pode gerenciar o cadastro de recursos";

    private final CadastroRecursoService cadastro;
    private final PesquisaRecursoService pesquisa;
    private final RecursoResolver recursoResolver;

    public CatalogoController(CadastroRecursoService cadastro, PesquisaRecursoService pesquisa,
                              RecursoResolver recursoResolver) {
        this.cadastro = cadastro;
        this.pesquisa = pesquisa;
        this.recursoResolver = recursoResolver;
    }

    public record CatalogoResposta(List<SalaResposta> salas, List<ProfessorResposta> professores,
                                   List<MaterialResposta> materiais) {
    }

    /** Recursos ativos, em ordem de nome. */
    @GetMapping("/recursos")
    public CatalogoResposta catalogo(@UsuarioAutenticado Usuario usuario) {
        Banco.seedSeNecessario();
        return new CatalogoResposta(
                Banco.salas.values().stream().filter(Sala::isAtivo).sorted(Comparator.comparing(Sala::getNome))
                        .map(SalaResposta::de).toList(),
                Banco.professores.values().stream().filter(Professor::isAtivo)
                        .sorted(Comparator.comparing(Professor::getNome)).map(ProfessorResposta::de).toList(),
                Banco.materiais.values().stream().filter(Material::isAtivo)
                        .sorted(Comparator.comparing(Material::getNome)).map(MaterialResposta::de).toList());
    }

    /** RF-09: professores com a competencia e sem reserva ativa ou manutencao no intervalo. */
    @GetMapping("/professores/disponiveis")
    public List<ProfessorResposta> professoresDisponiveis(
            @UsuarioAutenticado Usuario usuario, @RequestParam String competencia,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        return pesquisa.buscarProfessores(texto(competencia, "competencia"), inicio, fim).stream()
                .map(ProfessorResposta::de).toList();
    }

    /** RF-10: materiais com a quantidade livre pedida no intervalo e fora de manutencao. */
    @GetMapping("/materiais/disponiveis")
    public List<MaterialResposta> materiaisDisponiveis(
            @UsuarioAutenticado Usuario usuario, @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "1") int quantidade,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        if (quantidade < 1) {
            throw new RegraNegocioException(400, "Quantidade invalida: informe ao menos 1 unidade");
        }
        return pesquisa.buscarMateriais(opcional(tipo, "tipo"), quantidade, inicio, fim).stream()
                .map(MaterialResposta::de).toList();
    }

    @PostMapping("/salas")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaResposta cadastrarSala(@UsuarioAutenticado Usuario usuario, @RequestBody SalaRequisicao r) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        return SalaResposta.de(cadastro.cadastrarSala(opcional(r.nome(), "nome"), opcional(r.tipo(), "tipo"),
                numero(r.capacidade()), opcional(r.localizacao(), "localizacao"), Boolean.TRUE.equals(r.restrito())));
    }

    @PostMapping("/professores")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfessorResposta cadastrarProfessor(@UsuarioAutenticado Usuario usuario, @RequestBody ProfessorRequisicao r) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        return ProfessorResposta.de(cadastro.cadastrarProfessor(opcional(r.nome(), "nome"),
                opcional(r.identificadorInstitucional(), "identificadorInstitucional"), competencias(r.competencias())));
    }

    @PostMapping("/materiais")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialResposta cadastrarMaterial(@UsuarioAutenticado Usuario usuario, @RequestBody MaterialRequisicao r) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        return MaterialResposta.de(cadastro.cadastrarMaterial(opcional(r.nome(), "nome"), opcional(r.tipo(), "tipo"),
                numero(r.quantidadeTotal()), Boolean.TRUE.equals(r.restrito())));
    }

    @PostMapping("/salas/{id}/inativacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativarSala(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        cadastro.inativarSala((Sala) recursoResolver.resolver("SALA", id));
    }

    @PostMapping("/professores/{id}/inativacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativarProfessor(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        cadastro.inativarProfessor(id);
    }

    @PostMapping("/materiais/{id}/inativacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativarMaterial(@UsuarioAutenticado Usuario usuario, @PathVariable UUID id) {
        Perfis.exigir(usuario, SO_ADMIN, Perfil.ADMINISTRADOR);
        cadastro.inativarMaterial(id);
    }

    private static List<Competencia> competencias(List<String> nomes) {
        if (nomes == null) {
            return List.of();
        }
        if (nomes.size() > MAX_COMPETENCIAS) {
            throw new RegraNegocioException(400, "Informe no maximo " + MAX_COMPETENCIAS + " competencias");
        }
        return nomes.stream().map(nome -> new Competencia(texto(nome, "competencias"))).toList();
    }

    private static int numero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    /** Texto obrigatorio, sem espacos nas pontas e com tamanho limitado. */
    private static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(400, "Campo obrigatorio ausente: informe '" + campo + "'");
        }
        return opcional(valor, campo);
    }

    /** Ausente continua ausente (o servico decide se e obrigatorio); presente e aparado e limitado. */
    private static String opcional(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String aparado = valor.strip();
        if (aparado.length() > MAX_TEXTO) {
            throw new RegraNegocioException(400, "Campo '" + campo + "' excede " + MAX_TEXTO + " caracteres");
        }
        return aparado;
    }
}
