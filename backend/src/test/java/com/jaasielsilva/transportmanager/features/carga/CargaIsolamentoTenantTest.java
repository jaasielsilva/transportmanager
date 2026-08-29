package com.jaasielsilva.transportmanager.features.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.carga.service.CargaService;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Molde do kit — O TESTE DE ISOLAMENTO ENTRE TENANTS. Obrigatorio no modulo de
 * referencia (ARQUITETURA -> Multi-tenant) e item de go-live.
 *
 * Contra MySQL de verdade, e nao com mock, porque o que esta sendo testado NAO
 * e o nosso codigo: e o Hibernate aplicando o @TenantId em toda query. Mock
 * provaria apenas que o mock devolve o que mandamos devolver.
 *
 * O bug que este teste existe para pegar e o unico verdadeiramente inaceitavel
 * num SaaS: uma empresa enxergar o dado da outra. Ele e silencioso — nada
 * quebra, nada aparece no log, o cliente e quem descobre.
 *
 * Precisa de Docker. Sem Docker o teste e PULADO em vez de falhar: `mvn test`
 * na maquina do dev nao pode virar refem do Docker Desktop estar aberto. No
 * CI (ubuntu-latest) o Docker existe sempre, e la ele roda.
 */
@SpringBootTest
@Testcontainers
@EnabledIf("dockerDisponivel")
class CargaIsolamentoTenantTest {

    private static final Long EMPRESA_A = 1001L;
    private static final Long EMPRESA_B = 1002L;

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("kit_test");

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired CargaService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepararTenants() {
        TenantContext.clear();
        jdbc.update("DELETE FROM cargas WHERE empresa_id IN (?, ?)", EMPRESA_A, EMPRESA_B);
        jdbc.update("DELETE FROM empresas WHERE id IN (?, ?)", EMPRESA_A, EMPRESA_B);
        criarEmpresa(EMPRESA_A, "Empresa A", "11111111111111");
        criarEmpresa(EMPRESA_B, "Empresa B", "22222222222222");
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("uma empresa nao le, nao edita e nao exclui o registro da outra")
    void naoAtravessaTenant() {
        Detalhe daA = comTenant(EMPRESA_A, () -> service.criar(
                new SalvarRequest("Cadastro da A", "a@exemplo.com", null, "99999999999", null, null)));

        // Mesmo documento na empresa B: se a UNIQUE fosse global, isto falharia
        // — e o cliente numero 2 nunca conseguiria cadastrar.
        Detalhe daB = comTenant(EMPRESA_B, () -> service.criar(
                new SalvarRequest("Cadastro da B", "b@exemplo.com", null, "99999999999", null, null)));

        assertThat(daA.id()).isNotEqualTo(daB.id());

        comTenant(EMPRESA_B, () -> {
            // Listagem: so o proprio dado.
            var pagina = service.listar("", PageRequest.of(0, 20));
            assertThat(pagina.content()).extracting("nome").containsExactly("Cadastro da B");

            // Leitura direta pelo id da outra empresa: 404, nunca 403 — 403
            // confirmaria que aquele id existe em algum lugar da plataforma.
            assertThatThrownBy(() -> service.buscar(daA.id()))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            assertThatThrownBy(() -> service.atualizar(daA.id(),
                    new SalvarRequest("Invadido", null, null, null, null, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            assertThatThrownBy(() -> service.excluir(daA.id()))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            return null;
        });

        // O registro da A continua intacto e com o nome original.
        Detalhe conferencia = comTenant(EMPRESA_A, () -> service.buscar(daA.id()));
        assertThat(conferencia.nome()).isEqualTo("Cadastro da A");
    }

    @Test
    @DisplayName("empresa_id gravado vem do contexto, nao do request")
    void gravaOTenantDoContexto() {
        Detalhe criado = comTenant(EMPRESA_A, () -> service.criar(
                new SalvarRequest("Da empresa A", null, null, null, null, null)));

        Long empresaGravada = jdbc.queryForObject(
                "SELECT empresa_id FROM cargas WHERE id = ?", Long.class, criado.id());

        assertThat(empresaGravada).isEqualTo(EMPRESA_A);
    }

    @Test
    @DisplayName("excluido some da listagem e libera o documento para recadastro")
    void softDeleteLiberaAChaveUnica() {
        comTenant(EMPRESA_A, () -> {
            Detalhe primeiro = service.criar(new SalvarRequest(
                    "Primeiro", null, null, "55555555555", null, null));
            service.excluir(primeiro.id());

            assertThat(service.listar("", PageRequest.of(0, 20)).content()).isEmpty();

            // Recadastro do MESMO documento: e o caso que a sentinela deleted_seq
            // existe para permitir.
            Detalhe recadastro = service.criar(new SalvarRequest(
                    "Recadastrado", null, null, "55555555555", null, null));
            assertThat(recadastro.id()).isNotEqualTo(primeiro.id());
            return null;
        });
    }

    /** Executa um trecho como se a requisicao fosse daquele tenant. */
    private <T> T comTenant(Long empresaId, Supplier<T> acao) {
        TenantContext.set(empresaId);
        try {
            return acao.get();
        } finally {
            TenantContext.clear();
        }
    }

    private void criarEmpresa(Long id, String razaoSocial, String documento) {
        jdbc.update("""
                INSERT INTO empresas (id, razao_social, documento, plano_id, assinatura_status)
                VALUES (?, ?, ?, (SELECT id FROM planos WHERE codigo = 'TRIAL'), 'ACTIVE')
                """, id, razaoSocial, documento);
    }
}
