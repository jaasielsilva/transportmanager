package com.jaasielsilva.transportmanager.features.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.LimiteDoPlanoException;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.carga.entity.Carga;
import com.jaasielsilva.transportmanager.features.carga.repository.CargaRepository;
import com.jaasielsilva.transportmanager.features.carga.service.CargaService;
import com.jaasielsilva.transportmanager.features.platform.QuotaService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Molde do kit — teste de referencia do Service.
 *
 * O que se testa aqui e o que quebra dinheiro ou dado: quota do plano,
 * duplicidade, 404 entre tenants e exclusao logica. Getter e setter nao entram
 * na conta de cobertura de ninguem.
 *
 * O isolamento de verdade (uma empresa nao enxergar a outra) e provado no
 * CargaIsolamentoTenantTest, contra MySQL real — com mock nao se prova
 * comportamento do Hibernate.
 */
@ExtendWith(MockitoExtension.class)
class CargaServiceTest {

    private static final Long EMPRESA = 7L;

    @Mock CargaRepository repository;
    @Mock QuotaService quotaService;
    @Mock AuditoriaService auditoriaService;

    CargaService service;

    @BeforeEach
    void setUp() {
        service = new CargaService(repository, quotaService, auditoriaService);
        TenantContext.set(EMPRESA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("cria e devolve o detalhe, sem receber empresaId do request")
    void criaComSucesso() {
        when(repository.save(any())).thenAnswer(chamada -> {
            Carga salvo = chamada.getArgument(0);
            salvo.setId(1L);
            return salvo;
        });

        var detalhe = service.criar(new SalvarRequest(
                "  Maria Souza  ", "maria@exemplo.com", "11999999999", "12345678900", "", null));

        assertThat(detalhe.id()).isEqualTo(1L);
        assertThat(detalhe.nome()).isEqualTo("Maria Souza");     // trim aplicado
        assertThat(detalhe.observacao()).isNull();               // "" vira null
        assertThat(detalhe.ativo()).isTrue();                    // padrao da entity

        ArgumentCaptor<Carga> captor = ArgumentCaptor.forClass(Carga.class);
        verify(repository).save(captor.capture());
        // Quem grava o tenant e o @TenantId, nao o service.
        assertThat(captor.getValue().getEmpresaId()).isNull();
    }

    @Test
    @DisplayName("quota do plano estourada barra ANTES de gravar")
    void naoCriaAcimaDaQuota() {
        doThrow(new LimiteDoPlanoException("MAX_CADASTROS", 20))
                .when(quotaService).exigirCapacidade(anyString(), anyLong());

        assertThatThrownBy(() -> service.criar(novoRequest()))
                .isInstanceOf(LimiteDoPlanoException.class)
                .hasMessageContaining("upgrade");

        // Ordem importa: cliente sem quota nao pode deixar rastro no banco.
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("documento repetido na mesma empresa vira 409, nao erro do banco")
    void naoAceitaDocumentoDuplicado() {
        when(repository.existsByDocumentoAndDeletedAtIsNull("12345678900")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(novoRequest()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("12345678900");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("id inexistente (ou de outro tenant) vira 404, nunca 403")
    void naoEncontrado() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("excluir e logico e libera a chave UNIQUE")
    void excluiComSoftDelete() {
        var alvo = new Carga();
        alvo.setId(5L);
        alvo.setNome("Maria");
        alvo.setDocumento("12345678900");
        when(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(alvo));

        service.excluir(5L);

        assertThat(alvo.getDeletedAt()).isNotNull();
        // Sem a sentinela, recadastrar o mesmo documento ficaria impossivel.
        assertThat(alvo.getDeletedSeq()).isEqualTo(5L);
        verify(repository).save(alvo);
        verify(auditoriaService).registrar(eq(EMPRESA), eq("EXCLUSAO"), anyString(), eq(5L), any());
    }

    private SalvarRequest novoRequest() {
        return new SalvarRequest("Maria Souza", "maria@exemplo.com",
                "11999999999", "12345678900", null, null);
    }
}
