import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EstadoComponent } from '../../../shared/estado/estado.component';
import { ToastService } from '../../../core/services/toast.service';
import { Dashboard, WebhookFalha } from '../models/platform.model';
import { PlatformService } from '../services/platform.service';

/**
 * Molde do kit — features/platform/pages/painel.page.ts
 *
 * A TELA QUE O DONO ABRE DE MANHÃ. Não é o dashboard do cliente: aqui se
 * enxerga a base inteira.
 *
 * A ordem dos números não é estética. Primeiro **receita em risco** — quanto do
 * MRR está preso na régua de dunning — porque é o número que faz alguém agir
 * hoje. MRR sozinho é vaidade; MRR com uma fatia em risco é uma lista de
 * ligações a fazer.
 *
 * Cada cartão de problema é um LINK para a lista já filtrada. Métrica que não
 * leva a uma ação vira enfeite: o caminho entre "4 em atraso" e "quem são" tem
 * que ser um clique.
 */
@Component({
  selector: 'app-painel',
  imports: [RouterLink, CurrencyPipe, DecimalPipe, EstadoComponent],
  template: `
    <div class="barra-topo">
      <div>
        <h1>Plataforma</h1>
        <p class="texto-suave">Situação comercial da base inteira.</p>
      </div>
      <a class="btn btn-secundario" routerLink="/plataforma/tenants">Ver todos os tenants</a>
    </div>

    @if (carregando() || erro()) {
      <div class="card">
        <app-estado [carregando]="carregando()" [erro]="erro()" (acao)="carregar()" />
      </div>
    } @else if (dados(); as d) {
      <section class="cartoes">
        <!-- O par que conta a história: o que entra e o que está por um fio. -->
        <div class="cartao destaque">
          <span class="rotulo">MRR</span>
          <strong>{{ d.mrr | currency: 'BRL' }}</strong>
          <span class="texto-suave">só assinaturas ativas</span>
        </div>

        <a class="cartao alerta" routerLink="/plataforma/tenants" [queryParams]="{ situacao: 'PAST_DUE' }">
          <span class="rotulo">Receita em risco</span>
          <strong>{{ d.receitaEmRisco | currency: 'BRL' }}</strong>
          <span class="texto-suave">{{ d.tenantsEmAtraso }} tenant(s) na régua de cobrança</span>
        </a>

        <a class="cartao" routerLink="/plataforma/tenants" [queryParams]="{ situacao: 'ACTIVE' }">
          <span class="rotulo">Tenants ativos</span>
          <strong>{{ d.tenantsAtivos }}</strong>
        </a>

        <a class="cartao" routerLink="/plataforma/tenants" [queryParams]="{ situacao: 'TRIALING' }">
          <span class="rotulo">Em teste</span>
          <strong>{{ d.tenantsEmTrial }}</strong>
        </a>

        <a
          class="cartao"
          routerLink="/plataforma/tenants"
          [queryParams]="{ situacao: 'TRIAL_EXPIRANDO' }"
        >
          <span class="rotulo">Trials vencendo em 7 dias</span>
          <strong>{{ d.trialsExpirandoEm7Dias }}</strong>
          <span class="texto-suave">fila de contato comercial</span>
        </a>

        <div class="cartao" [class.alerta]="d.churnPercentual > 5">
          <span class="rotulo">Churn do mês</span>
          <strong>{{ d.churnPercentual | number: '1.0-2' }}%</strong>
          <span class="texto-suave">
            {{ d.canceladasNoMes }} cancelamento(s) · acima de 5% não se cresce
          </span>
        </div>

        <div class="cartao">
          <span class="rotulo">Ativação (30 dias)</span>
          <strong>{{ d.ativacaoPercentual | number: '1.0-2' }}%</strong>
          <span class="texto-suave">quem não ativa não converte</span>
        </div>
      </section>

      <div class="colunas">
        <section class="card">
          <h2>Uso por módulo</h2>
          <p class="texto-suave">
            Módulo que quase ninguém tem é candidato a corte ou a subir de plano; o mais usado é
            argumento de preço.
          </p>
          @if (d.usoPorModulo.length) {
            <ul class="barras">
              @for (m of d.usoPorModulo; track m.modulo) {
                <li>
                  <span class="nome">{{ m.modulo }}</span>
                  <span class="barra">
                    <span class="preenchida" [style.width.%]="percentual(m.tenants, d)"></span>
                  </span>
                  <span class="valor">{{ m.tenants }}</span>
                </li>
              }
            </ul>
          } @else {
            <p class="texto-suave">Nenhum tenant ativo ainda.</p>
          }
        </section>

        <section class="card">
          <h2>Webhooks não processados</h2>
          <p class="texto-suave">
            Evento do gateway que não entrou. Enquanto ele estiver aqui, a assinatura de alguém
            está desatualizada no nosso lado.
          </p>

          @if (webhooks().length) {
            <table class="tabela">
              <tbody>
                @for (w of webhooks(); track w.eventoId) {
                  <tr>
                    <td>
                      <strong>{{ w.tipo }}</strong>
                      <div class="texto-suave">{{ w.eventoId }}</div>
                      @if (w.erro) {
                        <div class="erro-campo">{{ w.erro }}</div>
                      }
                    </td>
                    <td style="width: 1%; white-space: nowrap">
                      <button
                        type="button"
                        class="btn btn-secundario"
                        [disabled]="reprocessando() === w.eventoId"
                        (click)="reprocessar(w)"
                      >
                        Reprocessar
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <p class="texto-suave">Nada pendente — todos os eventos foram processados.</p>
          }
        </section>
      </div>
    }
  `,
  styles: [
    `
      .cartoes {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
        gap: 12px;
        margin-bottom: 20px;
      }
      .cartao {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 16px;
        border: 1px solid var(--borda);
        border-radius: var(--raio);
        background: var(--card-bg);
        box-shadow: var(--sombra);
        color: inherit;
        text-decoration: none;
      }
      a.cartao:hover {
        border-color: var(--color-primary);
        text-decoration: none;
      }
      .cartao .rotulo {
        font-size: 0.8rem;
        text-transform: uppercase;
        letter-spacing: 0.03em;
        color: var(--texto-suave);
      }
      .cartao strong {
        font-size: 1.5rem;
      }
      .destaque {
        border-color: var(--color-primary);
      }
      .alerta strong {
        color: var(--erro);
      }
      .colunas {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
        gap: 16px;
        align-items: start;
      }
      .barras {
        list-style: none;
        margin: 12px 0 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .barras li {
        display: grid;
        grid-template-columns: 120px 1fr 40px;
        align-items: center;
        gap: 10px;
      }
      .barra {
        height: 8px;
        border-radius: 999px;
        background: var(--borda);
        overflow: hidden;
      }
      .preenchida {
        display: block;
        height: 100%;
        background: var(--color-primary);
      }
      .valor {
        text-align: right;
        color: var(--texto-suave);
      }
    `,
  ],
})
export class PainelPage {
  private readonly service = inject(PlatformService);
  private readonly toast = inject(ToastService);

  protected readonly dados = signal<Dashboard | null>(null);
  protected readonly webhooks = signal<WebhookFalha[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly reprocessando = signal<string | null>(null);

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.service.dashboard().subscribe({
      next: (d) => {
        this.dados.set(d);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar as métricas.');
        this.carregando.set(false);
      },
    });

    this.service.webhooksComFalha().subscribe({
      next: (lista) => this.webhooks.set(lista),
      // A fila de webhooks é secundária: se ela falhar, o painel continua útil.
      error: () => this.webhooks.set([]),
    });
  }

  protected percentual(tenants: number, d: Dashboard): number {
    const maior = Math.max(...d.usoPorModulo.map((m) => m.tenants), 1);
    return (tenants / maior) * 100;
  }

  protected reprocessar(w: WebhookFalha): void {
    this.reprocessando.set(w.eventoId);
    this.service.reprocessarWebhook(w.eventoId).subscribe({
      next: () => {
        this.toast.sucesso('Evento reenfileirado.');
        this.reprocessando.set(null);
        this.carregar();
      },
      error: () => this.reprocessando.set(null),
    });
  }
}
