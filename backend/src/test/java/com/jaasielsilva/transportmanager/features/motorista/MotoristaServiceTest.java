package com.jaasielsilva.transportmanager.features.motorista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.motorista.entity.Motorista;
import com.jaasielsilva.transportmanager.features.motorista.repository.MotoristaRepository;
import com.jaasielsilva.transportmanager.features.motorista.service.MotoristaService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Molde do kit — teste de referencia do Service (CargaServiceTest). */
@ExtendWith(MockitoExtension.class)
class MotoristaServiceTest {

    private static final Long EMPRESA = 7L;

    @Mock MotoristaRepository repository;
    @Mock AuditoriaService auditoriaService;

    MotoristaService service;

    @BeforeEach
    void setUp() {
        service = new MotoristaService(repository, auditoriaService);
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
            Motorista salvo = chamada.getArgument(0);
            salvo.setId(1L);
            return salvo;
        });

        var detalhe = service.criar(new SalvarRequest("  Joao Silva  ", "12345678900", "11999999999",
                "joao@exemplo.com", 55L, null));

        assertThat(detalhe.id()).isEqualTo(1L);
        assertThat(detalhe.nome()).isEqualTo("Joao Silva");
        assertThat(detalhe.ativo()).isTrue();

        ArgumentCaptor<Motorista> captor = ArgumentCaptor.forClass(Motorista.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmpresaId()).isNull(); // quem grava e o @TenantId
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("e-mail repetido na mesma empresa vira 409, nao erro do banco")
    void naoAceitaEmailDuplicado() {
        when(repository.existsByEmailAndDeletedAtIsNull("joao@exemplo.com")).thenReturn(true);

        var req = new SalvarRequest("Joao", null, null, "joao@exemplo.com", null, null);

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("joao@exemplo.com");

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
    @DisplayName("usuario sem vinculo de motorista vira 404 em /me")
    void usuarioSemVinculoDeMotoristaVira404() {
        when(repository.findByUsuarioIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorUsuarioLogado(123L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("excluir e logico e libera a chave UNIQUE")
    void excluiComSoftDelete() {
        var alvo = new Motorista();
        alvo.setId(5L);
        alvo.setNome("Joao");
        when(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(alvo));

        service.excluir(5L);

        assertThat(alvo.getDeletedAt()).isNotNull();
        assertThat(alvo.getDeletedSeq()).isEqualTo(5L);
        verify(repository).save(alvo);
    }
}
