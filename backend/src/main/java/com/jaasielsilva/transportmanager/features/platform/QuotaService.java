package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.LimiteDoPlanoException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Molde do kit — features/platform/QuotaService.java
 *
 * Limite de quantidade por plano (plano_limites). O par do @RequiresModule:
 * aquele responde "voce pode usar este modulo?", este responde "voce pode
 * criar mais um?".
 *
 * Fica num service e nao num interceptor porque a checagem depende do consumo
 * atual de CADA recurso — que so quem esta criando sabe contar.
 *
 * Chave sem linha em plano_limites = ILIMITADO, de proposito. Recurso novo
 * entra sem quota ate alguem decidir o numero; o contrario (bloquear tudo que
 * nao foi configurado) derruba feature nova no primeiro cliente.
 */
@Service
public class QuotaService {

    private final EmpresaRepository empresaRepository;

    public QuotaService(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    /**
     * Chamar ANTES de criar o recurso.
     *
     * Estourou -> 409 com caminho de upgrade. Erro de limite e momento
     * comercial: o cliente quer usar mais do produto do que pagou para usar.
     *
     * @param usoAtual quantos ja existem (o count vem do repositorio da feature)
     */
    public void exigirCapacidade(String chave, long usoAtual) {
        exigirCapacidade(TenantContext.getObrigatorio(), chave, usoAtual);
    }

    public void exigirCapacidade(Long empresaId, String chave, long usoAtual) {
        Optional<Long> limite = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.limiteDoPlano(empresaId, chave));

        if (limite.isPresent() && usoAtual >= limite.get()) {
            throw new LimiteDoPlanoException(chave, limite.get());
        }
    }

    /** Para a tela de plano do tenant: limite e consumo visiveis ANTES de estourar. */
    public Optional<Long> limite(String chave) {
        Long empresaId = TenantContext.getObrigatorio();
        return TenantContext.semFiltroDeTenant(
                () -> empresaRepository.limiteDoPlano(empresaId, chave));
    }
}
