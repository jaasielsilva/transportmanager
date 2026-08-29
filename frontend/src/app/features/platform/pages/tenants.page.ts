import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { PageResponse, paginaVazia } from '../../../core/models/api.model';
import { EstadoComponent } from '../../../shared/estado/estado.component';
import { SituacaoDeTenant, TenantResumo } from '../models/platform.model';
import { PlatformService } from '../services/platform.service';

/**
 * Molde do kit — features/platform/pages/tenants.page.ts
 *
 * A base de clientes numa tela. Os filtros são os que correspondem a uma ação
 * real do dono: quem está em atraso (cobrar), quem vence o trial esta semana
 * (ligar), quem cancelou (entender por quê).
 *
 * A ordenação vem fixa do backend — problema primeiro. Uma tabela ordenável
 * por qualquer coluna parece mais poderosa e faz todo mundo ver a mesma lista
 * genérica em vez do que precisa de atenção hoje.
 */
@Component({
  selector: 'app-tenants',
  imports: [RouterLink, DatePipe, EstadoComponent],
  template: `
    <div class="barra-topo">
      <div>
        <h1>Tenants</h1>
        <p class="texto-suave">{{ pagina().totalElements }} empresa(s) nesta visão</p>
      </div>
      <a class="btn btn-secundario" routerLink="/plataforma">Voltar ao painel</a>
    </div>

    <div class="filtros">
      @for (f of filtros; track f.valor) {
        <button
          type="button"
          class="chip"
          [class.ativo]="situacao() === f.valor"
          (click)="filtrar(f.valor)"
        >
          {{ f.rotulo }}
        </button>
      }
      <input
        type="search"
        placeholder="Buscar por nome ou documento"
        (input)="buscar($any($event.target).value)"
      />
    </div>

    <div class="card" style="padding: 0; overflow: hidden">
      @if (carregando() || erro() || pagina().totalElements === 0) {
        <app-estado
          [carregando]="carregando()"
          [erro]="erro()"
          [vazio]="pagina().totalElements === 0"
          tituloVazio="Nenhum tenant nesta visão"
          textoVazio="Troque o filtro para ver outras situações."
          (acao)="carregar(0)"
        />
      } @else {
        <table class="tabela">
          <thead>
            <tr>
              <th>Empresa</th>
              <th>Plano</th>
              <th>Situação</th>
              <th>Usuários</th>
              <th>Último acesso</th>
            </tr>
          </thead>
          <tbody>
            @for (t of pagina().content; track t.id) {
              <tr>
                <td><a [routerLink]="[t.id]">{{ t.razaoSocial }}</a></td>
                <td>{{ t.plano }}</td>
                <td>
                  <span class="selo" [class]="classeDoStatus(t)">{{ rotulo(t) }}</span>
                </td>
                <td>{{ t.usuarios }}</td>
                <!-- Tenant que nunca acessou é o trial que vai morrer sozinho. -->
                <td>{{ t.ultimoAcesso ? (t.ultimoAcesso | date: 'dd/MM/yy HH:mm') : 'nunca' }}</td>
              </tr>
            }
          </tbody>
        </table>

        @if (pagina().totalPages > 1) {
          <div class="paginacao">
            <button
              type="button"
              class="btn btn-secundario"
              [disabled]="pagina().page === 0"
              (click)="carregar(pagina().page - 1)"
            >
              Anterior
            </button>
            <span class="texto-suave">
              Página {{ pagina().page + 1 }} de {{ pagina().totalPages }}
            </span>
            <button
              type="button"
              class="btn btn-secundario"
              [disabled]="pagina().page + 1 >= pagina().totalPages"
              (click)="carregar(pagina().page + 1)"
            >
              Próxima
            </button>
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
      .filtros {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 12px;
      }
      .chip {
        padding: 6px 12px;
        border: 1px solid var(--borda);
        border-radius: 999px;
        background: var(--card-bg);
        font: inherit;
        font-size: 0.9rem;
        cursor: pointer;
      }
      .chip.ativo {
        background: var(--color-primary);
        border-color: var(--color-primary);
        color: #fff;
      }
      .filtros input {
        margin-left: auto;
        min-width: 240px;
        min-height: 34px;
        padding: 6px 10px;
        border: 1px solid var(--borda);
        border-radius: var(--raio-pequeno);
        font: inherit;
      }
      .paginacao {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 12px;
        padding: 12px;
        border-top: 1px solid var(--borda);
      }
    `,
  ],
})
export class TenantsPage {
  private readonly service = inject(PlatformService);
  private readonly rota = inject(ActivatedRoute);
  private readonly digitou = new Subject<string>();

  protected readonly filtros: { valor: SituacaoDeTenant; rotulo: string }[] = [
    { valor: '', rotulo: 'Todos' },
    { valor: 'PAST_DUE', rotulo: 'Em atraso' },
    { valor: 'TRIAL_EXPIRANDO', rotulo: 'Trial vencendo' },
    { valor: 'TRIALING', rotulo: 'Em teste' },
    { valor: 'ACTIVE', rotulo: 'Ativos' },
    { valor: 'CANCELED', rotulo: 'Cancelados' },
  ];

  protected readonly pagina = signal<PageResponse<TenantResumo>>(paginaVazia());
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly situacao = signal<SituacaoDeTenant>('');
  protected readonly termo = signal('');

  constructor() {
    // O painel manda o filtro pela URL: "4 em atraso" leva direto a quem são.
    const daUrl = this.rota.snapshot.queryParamMap.get('situacao') as SituacaoDeTenant | null;
    if (daUrl) {
      this.situacao.set(daUrl);
    }

    this.digitou
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((termo) => {
        this.termo.set(termo);
        this.carregar(0);
      });

    this.carregar(0);
  }

  protected filtrar(situacao: SituacaoDeTenant): void {
    this.situacao.set(situacao);
    this.carregar(0);
  }

  protected buscar(termo: string): void {
    this.digitou.next(termo);
  }

  protected rotulo(t: TenantResumo): string {
    switch (t.assinaturaStatus) {
      case 'TRIALING':
        return 'teste';
      case 'ACTIVE':
        return 'ativo';
      case 'PAST_DUE':
        // A etapa da régua é o que diz se ele ainda usa o sistema ou já parou.
        return `atraso · etapa ${t.dunningEtapa}`;
      case 'CANCELED':
        return 'cancelado';
      default:
        return t.assinaturaStatus;
    }
  }

  protected classeDoStatus(t: TenantResumo): string {
    if (t.assinaturaStatus === 'ACTIVE') {
      return 'selo-ativo';
    }
    return t.assinaturaStatus === 'PAST_DUE' || t.assinaturaStatus === 'CANCELED'
      ? 'selo-inativo'
      : '';
  }

  protected carregar(page: number): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.service.tenants(this.situacao(), this.termo(), page).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar os tenants.');
        this.carregando.set(false);
      },
    });
  }
}
