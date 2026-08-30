package com.jaasielsilva.transportmanager.features.rastreamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaasielsilva.transportmanager.exception.AcessoNegadoException;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.carga.entity.Carga;
import com.jaasielsilva.transportmanager.features.carga.repository.CargaRepository;
import com.jaasielsilva.transportmanager.features.motorista.entity.Motorista;
import com.jaasielsilva.transportmanager.features.motorista.repository.MotoristaRepository;
import com.jaasielsilva.transportmanager.features.rastreamento.dto.RastreamentoDtos.RegistrarPosicaoRequest;
import com.jaasielsilva.transportmanager.features.rastreamento.entity.PosicaoGps;
import com.jaasielsilva.transportmanager.features.rastreamento.repository.PosicaoGpsRepository;
import com.jaasielsilva.transportmanager.features.rastreamento.service.RastreamentoService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Molde do kit — teste de referencia do Service. Cobre os 3 casos de rejeicao
 * (carga nao em transito, carga sem motorista/motorista errado, motorista sem
 * usuarioId vinculado ao usuario autenticado) + o caso feliz.
 */
@ExtendWith(MockitoExtension.class)
class RastreamentoServiceTest {

    private static final Long USUARIO_LOGADO = 42L;

    @Mock CargaRepository cargaRepository;
    @Mock MotoristaRepository motoristaRepository;
    @Mock PosicaoGpsRepository posicaoGpsRepository;

    RastreamentoService service;

    @BeforeEach
    void setUp() {
        service = new RastreamentoService(cargaRepository, motoristaRepository, posicaoGpsRepository);
    }

    @Test
    @DisplayName("carga nao em transito vira 409, nao grava posicao")
    void cargaNaoEmTransitoRejeita() {
        var carga = cargaComStatus(1L, "PENDENTE");
        when(cargaRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(carga));

        assertThatThrownBy(() -> service.registrarPosicao(1L, USUARIO_LOGADO, umaPosicao()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("transito");

        verify(posicaoGpsRepository, never()).save(any());
    }

    @Test
    @DisplayName("carga sem motorista vinculado vira 403")
    void cargaSemMotoristaRejeita() {
        var carga = cargaComStatus(2L, "EM_TRANSITO");
        carga.setMotoristaId(null);
        when(cargaRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(carga));

        assertThatThrownBy(() -> service.registrarPosicao(2L, USUARIO_LOGADO, umaPosicao()))
                .isInstanceOf(AcessoNegadoException.class);

        verify(posicaoGpsRepository, never()).save(any());
    }

    @Test
    @DisplayName("motorista da carga nao e o usuario autenticado vira 403")
    void motoristaErradoRejeita() {
        var carga = cargaComStatus(3L, "EM_TRANSITO");
        carga.setMotoristaId(10L);
        when(cargaRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(carga));

        var motorista = new Motorista();
        motorista.setId(10L);
        motorista.setUsuarioId(999L); // outro usuario, nao o logado
        when(motoristaRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(motorista));

        assertThatThrownBy(() -> service.registrarPosicao(3L, USUARIO_LOGADO, umaPosicao()))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("motorista responsavel");

        verify(posicaoGpsRepository, never()).save(any());
    }

    @Test
    @DisplayName("motorista sem usuarioId vinculado tambem vira 403, nao NPE")
    void motoristaSemUsuarioVinculadoRejeita() {
        var carga = cargaComStatus(4L, "EM_TRANSITO");
        carga.setMotoristaId(11L);
        when(cargaRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(carga));

        var motorista = new Motorista();
        motorista.setId(11L);
        motorista.setUsuarioId(null);
        when(motoristaRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(motorista));

        assertThatThrownBy(() -> service.registrarPosicao(4L, USUARIO_LOGADO, umaPosicao()))
                .isInstanceOf(AcessoNegadoException.class);

        verify(posicaoGpsRepository, never()).save(any());
    }

    @Test
    @DisplayName("caso feliz: carga em transito + motorista correto grava a posicao")
    void casoFelizGravaPosicao() {
        var carga = cargaComStatus(5L, "EM_TRANSITO");
        carga.setMotoristaId(20L);
        when(cargaRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(carga));

        var motorista = new Motorista();
        motorista.setId(20L);
        motorista.setUsuarioId(USUARIO_LOGADO);
        when(motoristaRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(motorista));

        service.registrarPosicao(5L, USUARIO_LOGADO, umaPosicao());

        ArgumentCaptor<PosicaoGps> captor = ArgumentCaptor.forClass(PosicaoGps.class);
        verify(posicaoGpsRepository).save(captor.capture());
        assertThat(captor.getValue().getCargaId()).isEqualTo(5L);
        assertThat(captor.getValue().getMotoristaId()).isEqualTo(20L);
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo("-23.5505000");
        assertThat(captor.getValue().getRegistradoEm()).isNotNull();
    }

    @Test
    @DisplayName("carga inexistente (ou de outro tenant) vira 404")
    void cargaInexistenteVira404() {
        when(cargaRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarPosicao(99L, USUARIO_LOGADO, umaPosicao()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Carga cargaComStatus(Long id, String status) {
        var carga = new Carga();
        carga.setId(id);
        carga.setStatus(status);
        return carga;
    }

    private RegistrarPosicaoRequest umaPosicao() {
        return new RegistrarPosicaoRequest(new BigDecimal("-23.5505000"), new BigDecimal("-46.6333000"));
    }
}
