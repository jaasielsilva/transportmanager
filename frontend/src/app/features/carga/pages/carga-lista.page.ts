import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { PageResponse, paginaVazia } from '../../../core/models/api.model';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';
import { EstadoComponent } from '../../../shared/estado/estado.component';
import { CargaResumo } from '../models/carga.model';
import { CargaService } from '../services/carga.service';
import { classeStatus as classeDeStatus, rotuloStatus as rotuloDeStatus } from '../carga.status';

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/**
 * Molde do kit — LISTAGEM DE REFERENCIA. Copie esta estrutura em toda
 * listagem nova do projeto.
 *
 * O que ela ja resolve, e que costuma faltar quando se escreve do zero:
 *   - paginacao de verdade (a API nunca devolve lista inteira)
 *   - busca com debounce: sem ele, cada tecla digitada e uma consulta ao banco
 *   - os quatro estados da skill: carregando, vazio, erro e sucesso
 *   - confirmacao antes de excluir
 *   - modo somente leitura quando a assinatura esta em atraso — o cliente
 *     continua VENDO os dados dele, so nao grava. E isso que evita o churn
 *     por susto no dia em que o cartao falha.
 */
@Component({
  selector: 'app-carga-lista',
  imports: [RouterLink, EstadoComponent],
  template: `
    <div class="barra-topo">
      <div>
        <h1>Cargas</h1>
        <p class="texto-suave">{{ pagina().totalElements }} cadastrada(s)</p>
      </div>

      <div style="display: flex; gap: 8px; align-items: center">
        <input
          type="search"
          placeholder="Buscar por nome, e-mail ou documento"
          style="min-height: 38px; padding: 8px 10px; border: 1px solid var(--borda); border-radius: var(--raio-pequeno); min-width: 260px"
          (input)="buscar($any($event.target).value)"
        />
        @if (!somenteLeitura()) {
          <a class="btn" routerLink="novo">Novo</a>
        }
      </div>
    </div>

    <div class="card" style="padding: 0; overflow: hidden">
      @if (carregando() || erro() || pagina().totalElements === 0) {
        <app-estado
          [carregando]="carregando()"
          [erro]="erro()"
          [vazio]="pagina().totalElements === 0"
          [tituloVazio]="termo() ? 'Nenhum resultado' : 'Nenhuma carga ainda'"
          [textoVazio]="
            termo()
              ? 'Tente outro termo de busca.'
              : 'Cadastre a primeira para comecar a usar o sistema.'
          "
          [ctaVazio]="termo() || somenteLeitura() ? null : 'Cadastrar carga'"
          (acao)="aoAgirNoEstado()"
        />
      } @else {
        <table class="tabela">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Origem</th>
              <th>Destino</th>
              <th>Valor</th>
              <th>Status</th>
              <th style="width: 1%"></th>
            </tr>
          </thead>
          <tbody>
            @for (item of pagina().content; track item.id) {
              <tr>
                <td><a [routerLink]="[item.id]">{{ item.nome }}</a></td>
                <td>{{ rotuloCidade(item.origemCidade, item.origemUf) }}</td>
                <td>{{ rotuloCidade(item.destinoCidade, item.destinoUf) }}</td>
                <td>{{ formatarMoeda(item.valorFrete) }}</td>
                <td>
                  <span class="selo" [class]="classeStatus(item.status)">
                    {{ rotuloStatus(item.status) }}
                  </span>
                </td>
                <td style="white-space: nowrap">
                  <a class="btn btn-secundario" [routerLink]="[item.id]">Editar</a>
                  @if (podeExcluir()) {
                    <button type="button" class="btn btn-secundario" (click)="excluir(item)">
                      Excluir
                    </button>
                  }
                </td>
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
              (click)="irPara(pagina().page - 1)"
            >
              Anterior
            </button>
            <span class="texto-suave">
              Pagina {{ pagina().page + 1 }} de {{ pagina().totalPages }}
            </span>
            <button
              type="button"
              class="btn btn-secundario"
              [disabled]="pagina().page + 1 >= pagina().totalPages"
              (click)="irPara(pagina().page + 1)"
            >
              Proxima
            </button>
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
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
export class CargaListaPage {
  private readonly service = inject(CargaService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly confirmacao = inject(ConfirmacaoService);

  private readonly digitou = new Subject<string>();

  protected readonly pagina = signal<PageResponse<CargaResumo>>(paginaVazia());
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly termo = signal('');

  protected readonly somenteLeitura = this.auth.somenteLeitura;

  constructor() {
    // 350 ms: rapido o bastante para parecer instantaneo, lento o bastante
    // para nao transformar cada tecla numa consulta ao banco.
    this.digitou
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((termo) => {
        this.termo.set(termo);
        this.carregar(0);
      });

    this.carregar(0);
  }

  protected podeExcluir(): boolean {
    // Front esconde; quem bloqueia e o @PreAuthorize do backend.
    return this.auth.temRole('TENANT_ADMIN') && !this.somenteLeitura();
  }

  protected rotuloCidade(cidade: string | null, uf: string | null): string {
    if (!cidade) {
      return '—';
    }
    return uf ? `${cidade}/${uf}` : cidade;
  }

  protected rotuloStatus(status: string): string {
    return rotuloDeStatus(status);
  }

  protected classeStatus(status: string): string {
    return classeDeStatus(status);
  }

  protected formatarMoeda(valor: number | null): string {
    return valor == null ? '—' : moeda.format(valor);
  }

  protected buscar(termo: string): void {
    this.digitou.next(termo);
  }

  protected irPara(page: number): void {
    this.carregar(page);
  }

  protected aoAgirNoEstado(): void {
    // O mesmo botao serve para "tentar de novo" (erro) e para o CTA do vazio.
    if (this.erro()) {
      this.carregar(this.pagina().page);
    }
  }

  protected async excluir(item: CargaResumo): Promise<void> {
    const confirmado = await this.confirmacao.perguntar(
      'Excluir carga?',
      `"${item.nome}" sai das listagens. O registro fica guardado e pode ser recuperado pelo suporte.`,
      { confirmarTexto: 'Excluir', perigo: true },
    );
    if (!confirmado) {
      return;
    }

    this.service.excluir(item.id).subscribe(() => {
      this.toast.sucesso('Carga excluída.');
      // Recarrega a MESMA pagina: excluir o ultimo item de uma pagina deixaria
      // a tela vazia com o botao "proxima" ainda ativo.
      this.carregar(this.pagina().page);
    });
  }

  private carregar(page: number): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.service.listar(this.termo(), page).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.carregando.set(false);
      },
      error: () => {
        // O toast ja saiu no interceptor; aqui a tela precisa de um estado
        // proprio, senao fica um spinner eterno.
        this.erro.set('Nao foi possivel carregar a lista.');
        this.carregando.set(false);
      },
    });
  }
}
