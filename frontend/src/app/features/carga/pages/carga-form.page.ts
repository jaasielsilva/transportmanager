import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ErroDeCampo } from '../../../core/models/api.model';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
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
 */
@Component({
  selector: 'app-carga-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="barra-topo">
      <h1>{{ ehNovo() ? 'Novo carga' : 'Editar carga' }}</h1>
      <a class="btn btn-secundario" routerLink="/cargas">Voltar</a>
    </div>

    <form class="card" style="max-width: 640px" [formGroup]="form" (ngSubmit)="salvar()">
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

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    telefone: ['', [Validators.maxLength(20)]],
    documento: ['', [Validators.maxLength(20)]],
    observacao: ['', [Validators.maxLength(500)]],
    ativo: [true],
  });

  constructor() {
    if (!this.ehNovo()) {
      this.service.buscar(this.id).subscribe((dados) =>
        this.form.patchValue({
          nome: dados.nome,
          email: dados.email ?? '',
          telefone: dados.telefone ?? '',
          documento: dados.documento ?? '',
          observacao: dados.observacao ?? '',
          ativo: dados.ativo,
        }),
      );
    }
  }

  protected ehNovo(): boolean {
    return !this.id;
  }

  protected invalido(campo: 'nome' | 'email' | 'documento'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  protected mensagem(campo: 'nome' | 'email' | 'documento'): string {
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

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const dados: CargaSalvar = {
      ...this.form.getRawValue(),
      // "" nao e ausencia de valor: o backend normaliza, mas mandar null aqui
      // deixa o contrato honesto.
      email: this.form.getRawValue().email || null,
      telefone: this.form.getRawValue().telefone || null,
      documento: this.form.getRawValue().documento || null,
      observacao: this.form.getRawValue().observacao || null,
    };

    this.salvando.set(true);
    const requisicao = this.ehNovo()
      ? this.service.criar(dados)
      : this.service.atualizar(this.id, dados);

    requisicao.subscribe({
      next: () => {
        this.toast.sucesso(this.ehNovo() ? 'Carga cadastrado.' : 'Alteracoes salvas.');
        this.router.navigate(['/cargas']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.aplicarErrosDoServidor(e.error?.errors);
      },
    });
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
