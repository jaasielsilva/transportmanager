import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';
import { EstadoComponent } from '../../../shared/estado/estado.component';
import { DunningEvento, LimiteConsumo, TenantDetalhe } from '../models/platform.model';
import { PlatformService } from '../services/platform.service';

/**
 * Molde do kit — features/platform/pages/tenant-detalhe.page.ts
 *
 * A ficha de um cliente. Três coisas, nesta ordem, porque é a ordem em que as
 * perguntas aparecem no suporte:
 *
 *   1. como ele está (plano, situação, nível de acesso)
 *   2. o que a régua já fez com ele — o outro lado do job de dunning: ele age
 *      sozinho de madrugada, e aqui se vê o que aconteceu e quando
 *   3. quanto ele já usou do plano — a conversa de upgrade ANTES do 409
 *
 * O que NÃO tem aqui, de propósito: dado operacional do cliente. O painel
 * mostra situação comercial. Para ver o sistema pelos olhos dele existe a
 * impersonação, que é auditada (e não está implementada no kit).
 */
@Component({
  selector: 'app-tenant-detalhe',
  imports: [RouterLink, CurrencyPipe, DatePipe, EstadoComponent],
  template: `
    <div class="barra-topo">
      <div>
        <h1>{{ tenant()?.razaoSocial ?? 'Tenant' }}</h1>
        <p class="texto-suave">{{ tenant()?.documento }}</p>
      </div>
      <a class="btn btn-secundario" routerLink="/plataforma/tenants">Voltar</a>
    </div>

    @if (carregando() || erro()) {
      <div class="card">
        <app-estado [carregando]="carregando()" [erro]="erro()" (acao)="carregar()" />
      </div>
    } @else if (tenant(); as t) {
      <div class="colunas">
        <section class="card">
          <h2>Assinatura</h2>
          <dl class="linhas">
            <div><dt>Plano</dt><dd>{{ t.plano }}</dd></div>
            <div>
              <dt>Mensalidade</dt>
              <!--
                Comparação explícita com null: testar só a verdade do valor
                esconderia o plano de R$ 0,00 (trial e cortesia existem) atrás
                de um traço, como se o dado não existisse.
              -->
              <dd>{{ t.precoMensal !== null ? (t.precoMensal | currency: 'BRL') : '—' }}</dd>
            </div>
            <div><dt>Situação</dt><dd>{{ t.assinaturaStatus }}</dd></div>
            <div>
              <dt>Nível de acesso</dt>
              <dd [class.perigo]="t.nivelAcesso !== 'NORMAL'">{{ t.nivelAcesso }}</dd>
            </div>
            @if (t.pastDueDesde) {
              <div>
                <dt>Em atraso desde</dt>
                <dd>{{ t.pastDueDesde | date: 'dd/MM/yyyy' }} (etapa {{ t.dunningEtapa }})</dd>
              </div>
            }
            @if (t.trialExpiraEm) {
              <div><dt>Trial vence em</dt><dd>{{ t.trialExpiraEm | date: 'dd/MM/yyyy' }}</dd></div>
            }
            <div>
              <dt>Ativou</dt>
              <dd>{{ t.ativadaEm ? (t.ativadaEm | date: 'dd/MM/yyyy') : 'ainda não' }}</dd>
            </div>
            @if (t.purgeEm) {
              <div><dt>Exclusão definitiva</dt><dd>{{ t.purgeEm | date: 'dd/MM/yyyy' }}</dd></div>
            }
            <div><dt>Usuários</dt><dd>{{ t.usuarios }}</dd></div>
          </dl>

          @if (t.assinaturaStatus === 'PAST_DUE') {
            <!--
              Prorrogar não marca como pago: só empurra a régua. É a exceção
              individual e auditada que evita o UPDATE na mão em produção.
            -->
            <div class="acao-prazo">
              <label class="campo" style="margin: 0">
                <span>Prorrogar cobrança</span>
                <select #dias>
                  <option value="7">7 dias</option>
                  <option value="15">15 dias</option>
                  <option value="30">30 dias</option>
                </select>
              </label>
              <button type="button" class="btn" (click)="prorrogar(+dias.value)">Conceder prazo</button>
            </div>
            <p class="texto-suave">
              O cliente continua marcado como em atraso — só a contagem da régua volta ao início.
            </p>
          }
        </section>

        <section class="card">
          <h2>Consumo do plano</h2>
          @if (t.limites.length) {
            <ul class="barras">
              @for (l of t.limites; track l.chave) {
                <li>
                  <div class="linha-limite">
                    <span>{{ l.chave }}</span>
                    <span class="texto-suave">
                      @if (l.consumo < 0) {
                        limite {{ l.limite }} · consumo não medido
                      } @else {
                        {{ l.consumo }} de {{ l.limite }}
                      }
                    </span>
                  </div>
                  <span class="barra">
                    <span
                      class="preenchida"
                      [class.cheia]="percentual(l) >= 80"
                      [style.width.%]="percentual(l)"
                    ></span>
                  </span>
                </li>
              }
            </ul>
            @if (perto()) {
              <p class="texto-suave">
                Este cliente está perto do limite — momento de conversar sobre o plano maior, antes
                de ele esbarrar num erro.
              </p>
            }
          } @else {
            <p class="texto-suave">O plano deste tenant não tem limites configurados.</p>
          }
        </section>
      </div>

      <section class="card" style="margin-top: 16px">
        <h2>Histórico de cobrança</h2>
        @if (dunning().length) {
          <ol class="historico">
            @for (d of dunning(); track d.em) {
              <li>
                <span class="quando">{{ d.em | date: 'dd/MM/yy HH:mm' }}</span>
                <span class="selo">etapa {{ d.etapa }}</span>
                <strong>{{ d.acao }}</strong>
                @if (d.detalhes) {
                  <span class="texto-suave">{{ d.detalhes }}</span>
                }
              </li>
            }
          </ol>
        } @else {
          <p class="texto-suave">Nenhum evento de cobrança — este tenant nunca entrou na régua.</p>
        }
      </section>
    }
  `,
  styles: [
    `
      .colunas {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
        gap: 16px;
        align-items: start;
      }
      .linhas div {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        padding: 7px 0;
        border-bottom: 1px solid var(--borda);
      }
      dt {
        color: var(--texto-suave);
      }
      dd {
        margin: 0;
        font-weight: 500;
      }
      dd.perigo {
        color: var(--erro);
      }
      .acao-prazo {
        display: flex;
        align-items: flex-end;
        gap: 8px;
        margin-top: 16px;
      }
      .barras {
        list-style: none;
        margin: 12px 0 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .linha-limite {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 4px;
      }
      .barra {
        display: block;
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
      .preenchida.cheia {
        background: var(--aviso);
      }
      .historico {
        list-style: none;
        margin: 12px 0 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .historico li {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 10px;
        padding: 8px 0;
        border-bottom: 1px solid var(--borda);
      }
      .quando {
        color: var(--texto-suave);
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class TenantDetalhePage {
  private readonly service = inject(PlatformService);
  private readonly rota = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly confirmacao = inject(ConfirmacaoService);

  private readonly id = Number(this.rota.snapshot.paramMap.get('id'));

  protected readonly tenant = signal<TenantDetalhe | null>(null);
  protected readonly dunning = signal<DunningEvento[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  /** Alguém acima de 80% de qualquer quota: gatilho de conversa comercial. */
  protected readonly perto = computed(() =>
    (this.tenant()?.limites ?? []).some((l) => this.percentual(l) >= 80),
  );

  constructor() {
    this.carregar();
  }

  protected percentual(l: LimiteConsumo): number {
    if (l.consumo < 0 || l.limite <= 0) {
      return 0;
    }
    return Math.min((l.consumo / l.limite) * 100, 100);
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.service.tenant(this.id).subscribe({
      next: (t) => {
        this.tenant.set(t);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar este tenant.');
        this.carregando.set(false);
      },
    });

    this.service.dunning(this.id).subscribe({
      next: (lista) => this.dunning.set(lista),
      error: () => this.dunning.set([]),
    });
  }

  protected async prorrogar(dias: number): Promise<void> {
    const confirmado = await this.confirmacao.perguntar(
      `Conceder ${dias} dias?`,
      'A régua de cobrança volta ao início para este cliente. Ele continua marcado como em atraso, e a concessão fica registrada na auditoria.',
      { confirmarTexto: 'Conceder prazo' },
    );
    if (!confirmado) {
      return;
    }

    this.service.prorrogar(this.id, dias).subscribe(() => {
      this.toast.sucesso(`Cobrança prorrogada por ${dias} dias.`);
      this.carregar();
    });
  }
}
