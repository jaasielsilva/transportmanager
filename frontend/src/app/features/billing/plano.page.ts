import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * Molde do kit — features/billing/plano.page.ts
 *
 * Onde termina quem esbarrou no plano: modulo nao contratado (403 /
 * moduloGuard) ou quota estourada (409).
 *
 * Erro de limite e momento comercial, nao mensagem de falha: o cliente esta
 * tentando usar MAIS do produto do que comprou. Uma tela que explica o que ele
 * ganharia converte; um toast "sem permissao" so irrita.
 *
 * PENDENTE no kit: precos, comparativo de planos e o botao de upgrade de
 * verdade — dependem do GatewayBilling do projeto. O que existe aqui e o que o
 * /me ja responde, sem inventar dado.
 */
@Component({
  selector: 'app-plano',
  imports: [RouterLink],
  template: `
    <h1>Plano e cobranca</h1>

    @if (moduloBloqueado()) {
      <div class="card" style="max-width: 640px; margin-top: 16px; border-color: var(--color-primary)">
        <h2>Este recurso esta em outro plano</h2>
        <p>
          O modulo <strong>{{ moduloBloqueado() }}</strong> nao faz parte do seu plano atual. Fale com
          a gente para liberar — a mudanca vale na hora.
        </p>
        <a class="btn" href="mailto:silvajasiel30@gmail.com?subject=Upgrade de plano">Quero este recurso</a>
      </div>
    }

    <div class="card" style="max-width: 640px; margin-top: 16px">
      <h2>Sua assinatura</h2>
      <dl class="linhas">
        <div>
          <dt>Empresa</dt>
          <dd>{{ usuario()?.empresa }}</dd>
        </div>
        <div>
          <dt>Situacao</dt>
          <dd>{{ rotulo(usuario()?.assinaturaStatus) }}</dd>
        </div>
        <div>
          <dt>Nivel de acesso</dt>
          <dd>{{ usuario()?.nivelAcesso }}</dd>
        </div>
      </dl>

      @if (usuario()?.assinaturaStatus === 'PAST_DUE') {
        <a class="btn" routerLink="/assinatura">Regularizar pagamento</a>
      }
    </div>

    <div class="card" style="max-width: 640px; margin-top: 16px">
      <h2>Modulos incluidos</h2>
      @if (modulos().length) {
        <ul>
          @for (m of modulos(); track m) {
            <li>{{ m }}</li>
          }
        </ul>
      } @else {
        <p class="texto-suave">Nenhum modulo habilitado no plano atual.</p>
      }
    </div>
  `,
  styles: [
    `
      .linhas div {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        padding: 8px 0;
        border-bottom: 1px solid var(--borda);
      }
      dt {
        color: var(--texto-suave);
      }
      dd {
        margin: 0;
        font-weight: 500;
      }
    `,
  ],
})
export class PlanoPage {
  private readonly auth = inject(AuthService);
  private readonly rota = inject(ActivatedRoute);

  protected readonly usuario = this.auth.usuario;
  protected readonly modulos = computed(() => this.usuario()?.modulos ?? []);

  /** Preenchido pelo moduloGuard quando a pessoa foi barrada numa rota. */
  protected readonly moduloBloqueado = computed(() =>
    this.rota.snapshot.queryParamMap.get('modulo'),
  );

  protected rotulo(status?: string): string {
    switch (status) {
      case 'TRIALING':
        return 'Periodo de teste';
      case 'ACTIVE':
        return 'Ativa';
      case 'PAST_DUE':
        return 'Pagamento em atraso';
      case 'CANCELED':
        return 'Cancelada';
      default:
        return '—';
    }
  }
}
