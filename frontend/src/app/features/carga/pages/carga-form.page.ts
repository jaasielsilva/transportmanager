import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ErroDeCampo } from '../../../core/models/api.model';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  classeStatus as classeDeStatus,
  rotuloStatus as rotuloDeStatus,
  STATUS_TRANSICOES,
} from '../carga.status';
import { CargaSalvar } from '../models/carga.model';
import { CargaService } from '../services/carga.service';

/**
 * Molde do kit — FORMULARIO DE REFERENCIA (criar e editar na mesma tela).
 *
 * Uma tela so para os dois casos porque os campos e as regras sao os mesmos —
 * duas telas identicas so garantem que uma vai ganhar uma validacao que a
 * outra nao tem.
 *
 * O detalhe que quase sempre falta: os erros por campo (`errors[]` do 400) sao
 * aplicados nos controles do formulario. Sem isso, o backend diz exatamente
 * qual campo esta errado e a tela mostra um toast generico.
 *
 * Status fica FORA do form: na criacao nasce PENDENTE e a troca usa o endpoint
 * proprio (`PATCH /{id}/status`) com validacao de transicao. Aqui ele so aparece
 * como selo somente-leitura na edicao.
 */
@Component({
  selector: 'app-carga-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="barra-topo">
      <div>
        <h1>{{ ehNovo() ? 'Nova carga' : 'Editar carga' }}</h1>
        @if (!ehNovo() && status()) {
          <span class="selo" [class]="classeStatus(status())">{{ rotuloStatus(status()) }}</span>
        }
      </div>
      <a class="btn btn-secundario" routerLink="/cargas">Voltar</a>
    </div>

    @if (!ehNovo() && status()) {
      <div
        class="card"
        style="max-width: 720px; display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px"
      >
        <div style="display: flex; align-items: center; gap: 8px">
          <span class="texto-suave">Status</span>
          <span class="selo" [class]="classeStatus(status())">{{ rotuloStatus(status()) }}</span>
        </div>

        @if (transicoes().length) {
          <div style="display: flex; gap: 8px; align-items: center">
            <select
              [formControl]="statusControle"
              style="min-height: 38px; padding: 8px 10px; border: 1px solid var(--borda); border-radius: var(--raio-pequeno); background: var(--card-bg)"
            >
              @for (opcao of transicoes(); track opcao) {
                <option [value]="opcao">{{ rotuloStatus(opcao) }}</option>
              }
            </select>
            <button
              type="button"
              class="btn"
              [disabled]="salvando() || somenteLeitura() || !statusControle.value"
              (click)="trocarStatus()"
            >
              {{ salvando() ? 'Aplicando...' : 'Aplicar' }}
            </button>
          </div>
        } @else {
          <span class="texto-suave">Status final — nao ha mais transicoes.</span>
        }
      </div>
    }

    <form class="card" style="max-width: 720px" [formGroup]="form" (ngSubmit)="salvar()">
      <h2>Identificacao</h2>

      <label class="campo">
        <span>Nome *</span>
        <input formControlName="nome" [class.invalido]="invalido('nome')" />
        @if (invalido('nome')) {
          <span class="erro-campo">{{ mensagem('nome') }}</span>
        }
      </label>

      <label class="campo">
        <span>E-mail</span>
        <input type="email" formControlName="email" [class.invalido]="invalido('email')" />
        @if (invalido('email')) {
          <span class="erro-campo">{{ mensagem('email') }}</span>
        }
      </label>

      <label class="campo">
        <span>Telefone</span>
        <input formControlName="telefone" />
      </label>

      <label class="campo">
        <span>Documento</span>
        <input formControlName="documento" [class.invalido]="invalido('documento')" />
        @if (invalido('documento')) {
          <span class="erro-campo">{{ mensagem('documento') }}</span>
        }
      </label>

      <label class="campo">
        <span>Observacao</span>
        <textarea rows="3" formControlName="observacao"></textarea>
      </label>

      <h2>Transporte</h2>

      <label class="campo">
        <span>Endereco de origem</span>
        <input formControlName="origemEndereco" />
      </label>

      <div style="display: flex; gap: 8px">
        <label class="campo" style="flex: 1">
          <span>Cidade de origem *</span>
          <input formControlName="origemCidade" [class.invalido]="invalido('origemCidade')" />
          @if (invalido('origemCidade')) {
            <span class="erro-campo">{{ mensagem('origemCidade') }}</span>
          }
        </label>
        <label class="campo" style="max-width: 72px">
          <span>UF</span>
          <input formControlName="origemUf" maxlength="2" />
        </label>
      </div>

      <label class="campo">
        <span>Endereco de destino</span>
        <input formControlName="destinoEndereco" />
      </label>

      <div style="display: flex; gap: 8px">
        <label class="campo" style="flex: 1">
          <span>Cidade de destino *</span>
          <input formControlName="destinoCidade" [class.invalido]="invalido('destinoCidade')" />
          @if (invalido('destinoCidade')) {
            <span class="erro-campo">{{ mensagem('destinoCidade') }}</span>
          }
        </label>
        <label class="campo" style="max-width: 72px">
          <span>UF</span>
          <input formControlName="destinoUf" maxlength="2" />
        </label>
      </div>

      <div style="display: flex; gap: 8px">
        <label class="campo" style="flex: 1">
          <span>Peso (kg)</span>
          <input type="number" min="0" step="0.01" formControlName="peso" />
        </label>
        <label class="campo" style="flex: 1">
          <span>Valor do frete (R$)</span>
          <input type="number" min="0" step="0.01" formControlName="valorFrete" />
        </label>
      </div>

      <div style="display: flex; gap: 8px">
        <label class="campo" style="flex: 1">
          <span>Coleta</span>
          <input type="datetime-local" formControlName="dataColeta" />
        </label>
        <label class="campo" style="flex: 1">
          <span>Entrega prevista</span>
          <input type="datetime-local" formControlName="dataEntregaPrevista" />
        </label>
        <label class="campo" style="flex: 1">
          <span>Entrega real</span>
          <input type="datetime-local" formControlName="dataEntregaReal" />
        </label>
      </div>

      <div style="display: flex; gap: 8px">
        <label class="campo" style="flex: 1">
          <span>Distancia (km)</span>
          <input type="number" min="0" step="1" formControlName="distanciaKm" />
        </label>
        <label class="campo" style="flex: 1">
          <span>Tempo estimado (min)</span>
          <input type="number" min="0" step="1" formControlName="tempoEstimadoMinutos" />
        </label>
      </div>

      <label class="campo" style="display: flex; align-items: center; gap: 8px">
        <input type="checkbox" formControlName="ativo" style="width: auto; min-height: auto" />
        <span style="margin: 0">Ativo</span>
      </label>

      <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px">
        <a class="btn btn-secundario" routerLink="/cargas">Cancelar</a>
        <button type="submit" class="btn" [disabled]="salvando() || somenteLeitura()">
          {{ salvando() ? 'Salvando...' : 'Salvar' }}
        </button>
      </div>

      @if (somenteLeitura()) {
        <p class="texto-suave" style="text-align: right">
          Assinatura em atraso: no momento voce pode consultar, mas nao alterar.
        </p>
      }
    </form>
  `,
})
export class CargaFormPage {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CargaService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  private readonly id = Number(this.rota.snapshot.paramMap.get('id'));

  protected readonly salvando = signal(false);
  protected readonly somenteLeitura = this.auth.somenteLeitura;
  protected readonly status = signal<string | null>(null);

  /** Transicoes validas a partir do status atual — espelha a regra do backend. */
  protected readonly transicoes = computed(() => {
    const atual = this.status();
    return atual ? (STATUS_TRANSICOES[atual] ?? []) : [];
  });

  /** Seletor do card de status; reiniciado a cada mudanca de status. */
  protected readonly statusControle = new FormControl<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    telefone: ['', [Validators.maxLength(20)]],
    documento: ['', [Validators.maxLength(20)]],
    observacao: ['', [Validators.maxLength(500)]],
    ativo: [true],
    origemEndereco: ['', [Validators.maxLength(255)]],
    origemCidade: ['', [Validators.required, Validators.maxLength(100)]],
    origemUf: ['', [Validators.maxLength(2)]],
    destinoEndereco: ['', [Validators.maxLength(255)]],
    destinoCidade: ['', [Validators.required, Validators.maxLength(100)]],
    destinoUf: ['', [Validators.maxLength(2)]],
    peso: [''],
    valorFrete: [''],
    dataColeta: [''],
    dataEntregaPrevista: [''],
    dataEntregaReal: [''],
    distanciaKm: [''],
    tempoEstimadoMinutos: [''],
  });

  constructor() {
    if (!this.ehNovo()) {
      this.service.buscar(this.id).subscribe((dados) => {
        this.form.patchValue({
          nome: dados.nome,
          email: dados.email ?? '',
          telefone: dados.telefone ?? '',
          documento: dados.documento ?? '',
          observacao: dados.observacao ?? '',
          ativo: dados.ativo,
          origemEndereco: dados.origemEndereco ?? '',
          origemCidade: dados.origemCidade ?? '',
          origemUf: dados.origemUf ?? '',
          destinoEndereco: dados.destinoEndereco ?? '',
          destinoCidade: dados.destinoCidade ?? '',
          destinoUf: dados.destinoUf ?? '',
          peso: dados.peso != null ? String(dados.peso) : '',
          valorFrete: dados.valorFrete != null ? String(dados.valorFrete) : '',
          dataColeta: this.paraDatetimeLocal(dados.dataColeta),
          dataEntregaPrevista: this.paraDatetimeLocal(dados.dataEntregaPrevista),
          dataEntregaReal: this.paraDatetimeLocal(dados.dataEntregaReal),
          distanciaKm: dados.distanciaKm != null ? String(dados.distanciaKm) : '',
          tempoEstimadoMinutos:
            dados.tempoEstimadoMinutos != null ? String(dados.tempoEstimadoMinutos) : '',
        });
        this.status.set(dados.status);
        this.statusControle.setValue((STATUS_TRANSICOES[dados.status] ?? [])[0] ?? null);
      });
    }
  }

  protected ehNovo(): boolean {
    return !this.id;
  }

  protected rotuloStatus(status: string | null): string {
    return rotuloDeStatus(status);
  }

  protected classeStatus(status: string | null): string {
    return classeDeStatus(status);
  }

  protected invalido(campo: 'nome' | 'email' | 'documento' | 'origemCidade' | 'destinoCidade'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  protected mensagem(campo: 'nome' | 'email' | 'documento' | 'origemCidade' | 'destinoCidade'): string {
    const erros = this.form.controls[campo].errors ?? {};
    // A mensagem vinda do backend (400 por campo) tem prioridade: ela conhece
    // regras que o front nao tem como conhecer.
    if (erros['servidor']) {
      return erros['servidor'] as string;
    }
    if (erros['required']) {
      return 'Campo obrigatorio.';
    }
    if (erros['email']) {
      return 'E-mail invalido.';
    }
    return 'Valor invalido.';
  }

  /**
   * Troca o status via endpoint proprio. Apos aplicar, o selo e as transicoes
   * da tela sao recalculados a partir do status devolvido pelo backend.
   */
  protected trocarStatus(): void {
    const novo = this.statusControle.value;
    if (!novo || this.ehNovo() || this.somenteLeitura()) {
      return;
    }

    this.salvando.set(true);
    this.service.atualizarStatus(this.id, { status: novo }).subscribe({
      next: (detalhe) => {
        this.salvando.set(false);
        this.status.set(detalhe.status);
        this.statusControle.setValue((STATUS_TRANSICOES[detalhe.status] ?? [])[0] ?? null);
        this.toast.sucesso('Status atualizado.');
      },
      error: () => {
        // O toast generico ja saiu no interceptor.
        this.salvando.set(false);
      },
    });
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const dados: CargaSalvar = {
      nome: raw.nome,
      // "" nao e ausencia de valor: o backend normaliza, mas mandar null aqui
      // deixa o contrato honesto.
      email: raw.email || null,
      telefone: raw.telefone || null,
      documento: raw.documento || null,
      observacao: raw.observacao || null,
      ativo: raw.ativo,
      origemEndereco: raw.origemEndereco || null,
      origemCidade: raw.origemCidade,
      origemUf: raw.origemUf || null,
      destinoEndereco: raw.destinoEndereco || null,
      destinoCidade: raw.destinoCidade,
      destinoUf: raw.destinoUf || null,
      peso: this.paraNumero(raw.peso),
      valorFrete: this.paraNumero(raw.valorFrete),
      status: null,
      motoristaId: null,
      clienteId: null,
      dataColeta: this.deDatetimeLocal(raw.dataColeta),
      dataEntregaPrevista: this.deDatetimeLocal(raw.dataEntregaPrevista),
      dataEntregaReal: this.deDatetimeLocal(raw.dataEntregaReal),
      distanciaKm: this.paraInteiro(raw.distanciaKm),
      tempoEstimadoMinutos: this.paraInteiro(raw.tempoEstimadoMinutos),
    };

    this.salvando.set(true);
    const requisicao = this.ehNovo()
      ? this.service.criar(dados)
      : this.service.atualizar(this.id, dados);

    requisicao.subscribe({
      next: () => {
        this.toast.sucesso(this.ehNovo() ? 'Carga cadastrada.' : 'Alteracoes salvas.');
        this.router.navigate(['/cargas']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.aplicarErrosDoServidor(e.error?.errors);
      },
    });
  }

  /** `datetime-local` devolve "yyyy-MM-ddTHH:mm"; o backend espera ISO com segundos. */
  private deDatetimeLocal(v: string): string | null {
    if (!v) {
      return null;
    }
    return v.length === 16 ? `${v}:00` : v;
  }

  /** Inverso: corta os segundos do ISO para o `datetime-local` aceitar. */
  private paraDatetimeLocal(iso: string | null): string {
    return iso && iso.length >= 16 ? iso.slice(0, 16) : '';
  }

  private paraNumero(v: string): number | null {
    if (v === '' || v == null) {
      return null;
    }
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }

  private paraInteiro(v: string): number | null {
    if (v === '' || v == null) {
      return null;
    }
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : null;
  }

  /** Traduz o `errors[]` do ApiResponse em erro inline no campo certo. */
  private aplicarErrosDoServidor(erros?: ErroDeCampo[] | null): void {
    for (const erro of erros ?? []) {
      const controle = this.form.get(erro.field);
      if (controle) {
        controle.setErrors({ servidor: erro.message });
        controle.markAsTouched();
      }
    }
  }
}
