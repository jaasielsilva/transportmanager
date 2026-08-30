import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { PageResponse, paginaVazia } from '../../../core/models/api.model';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';
import { EstadoComponent } from '../../../shared/estado/estado.component';
import { MotoristaResumo } from '../models/motorista.model';
import { MotoristaService } from '../services/motorista.service';

/**
 * Molde do kit — features/carga/pages/carga-lista.page.ts. Mesma estrutura:
 * paginacao, busca com debounce, os quatro estados e confirmacao de exclusao.
 */
@Component({
  selector: 'app-motorista-lista',
  imports: [RouterLink, EstadoComponent],
  template: `
    <div class="barra-topo">
      <div>
        <h1>Motoristas</h1>
        <p class="texto-suave">{{ pagina().totalElements }} cadastrado(s)</p>
      </div>

      <div style="display: flex; gap: 8px; align-items: center">
        <input
          type="search"
          placeholder="Buscar por nome ou e-mail"
          style="min-height: 38px; padding: 8px 10px; border: 1px solid var(--borda); border-radius: var(--raio-pequeno); min-width: 260px"
          (input)="buscar($any($event.target).value)"
        />
        <a class="btn" routerLink="novo">Novo</a>
      </div>
    </div>

    <div class="card" style="padding: 0; overflow: hidden">
      @if (carregando() || erro() || pagina().totalElements === 0) {
        <app-estado
          [carregando]="carregando()"
          [erro]="erro()"
          [vazio]="pagina().totalElements === 0"
          [tituloVazio]="termo() ? 'Nenhum resultado' : 'Nenhum motorista ainda'"
          [textoVazio]="
            termo()
              ? 'Tente outro termo de busca.'
              : 'Cadastre o primeiro motorista para vincular as cargas.'
          "
          [ctaVazio]="termo() ? null : 'Cadastrar motorista'"
          (acao)="aoAgirNoEstado()"
        />
      } @else {
        <table class="tabela">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Telefone</th>
              <th>Status</th>
              <th style="width: 1%"></th>
            </tr>
          </thead>
          <tbody>
            @for (item of pagina().content; track item.id) {
              <tr>
                <td><a [routerLink]="[item.id]">{{ item.nome }}</a></td>
                <td>{{ item.telefone || '—' }}</td>
                <td>
                  <span class="selo" [class]="item.ativo ? 'selo-ativo' : 'selo-inativo'">
                    {{ item.ativo ? 'Ativo' : 'Inativo' }}
                  </span>
                </td>
                <td style="white-space: nowrap">
                  <a class="btn btn-secundario" [routerLink]="[item.id]">Editar</a>
                  <button type="button" class="btn btn-secundario" (click)="excluir(item)">
                    Excluir
                  </button>
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
            <span class="texto-suave">Pagina {{ pagina().page + 1 }} de {{ pagina().totalPages }}</span>
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
export class MotoristaListaPage {
  private readonly service = inject(MotoristaService);
  private readonly toast = inject(ToastService);
  private readonly confirmacao = inject(ConfirmacaoService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  private readonly digitou = new Subject<string>();

  protected readonly pagina = signal<PageResponse<MotoristaResumo>>(paginaVazia());
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly termo = signal('');

  constructor() {
    this.digitou
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((termo) => {
        this.termo.set(termo);
        this.carregar(0);
      });

    this.carregar(0);
  }

  protected buscar(termo: string): void {
    this.digitou.next(termo);
  }

  protected irPara(page: number): void {
    this.carregar(page);
  }

  protected aoAgirNoEstado(): void {
    // O mesmo botao serve para "tentar de novo" (erro) e para o CTA do vazio:
    // com erro, recarrega; vazio, leva para o cadastro.
    if (this.erro()) {
      this.carregar(this.pagina().page);
    } else {
      this.router.navigate(['novo'], { relativeTo: this.rota });
    }
  }

  protected async excluir(item: MotoristaResumo): Promise<void> {
    const confirmado = await this.confirmacao.perguntar(
      'Excluir motorista?',
      `"${item.nome}" sai das listagens. O registro fica guardado e pode ser recuperado pelo suporte.`,
      { confirmarTexto: 'Excluir', perigo: true },
    );
    if (!confirmado) {
      return;
    }

    this.service.excluir(item.id).subscribe(() => {
      this.toast.sucesso('Motorista excluido.');
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
        this.erro.set('Nao foi possivel carregar a lista.');
        this.carregando.set(false);
      },
    });
  }
}
