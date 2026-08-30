import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ErroDeCampo } from '../../../core/models/api.model';
import { ToastService } from '../../../core/services/toast.service';
import { MotoristaSalvar } from '../models/motorista.model';
import { MotoristaService } from '../services/motorista.service';

/**
 * Molde do kit — features/carga/pages/carga-form.page.ts. Uma tela so para
 * criar e editar: os campos e as regras sao os mesmos.
 */
@Component({
  selector: 'app-motorista-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="barra-topo">
      <h1>{{ ehNovo() ? 'Novo motorista' : 'Editar motorista' }}</h1>
      <a class="btn btn-secundario" routerLink="/motoristas">Voltar</a>
    </div>

    <form class="card" style="max-width: 560px" [formGroup]="form" (ngSubmit)="salvar()">
      <label class="campo">
        <span>Nome *</span>
        <input formControlName="nome" [class.invalido]="invalido('nome')" />
        @if (invalido('nome')) {
          <span class="erro-campo">{{ mensagem('nome') }}</span>
        }
      </label>

      <label class="campo">
        <span>CNH</span>
        <input formControlName="cnh" />
      </label>

      <label class="campo">
        <span>Telefone</span>
        <input formControlName="telefone" />
      </label>

      <label class="campo">
        <span>E-mail</span>
        <input type="email" formControlName="email" [class.invalido]="invalido('email')" />
        @if (invalido('email')) {
          <span class="erro-campo">{{ mensagem('email') }}</span>
        }
      </label>

      <label class="campo">
        <span>ID do usuario vinculado (login)</span>
        <input type="number" min="1" formControlName="usuarioId" />
        <span class="texto-suave">
          Opcional. Vincula este motorista a um login para ele acessar "Minhas entregas".
        </span>
      </label>

      <label class="campo" style="display: flex; align-items: center; gap: 8px">
        <input type="checkbox" formControlName="ativo" style="width: auto; min-height: auto" />
        <span style="margin: 0">Ativo</span>
      </label>

      <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px">
        <a class="btn btn-secundario" routerLink="/motoristas">Cancelar</a>
        <button type="submit" class="btn" [disabled]="salvando()">
          {{ salvando() ? 'Salvando...' : 'Salvar' }}
        </button>
      </div>
    </form>
  `,
})
export class MotoristaFormPage {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MotoristaService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  private readonly id = Number(this.rota.snapshot.paramMap.get('id'));

  protected readonly salvando = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    cnh: ['', [Validators.maxLength(20)]],
    telefone: ['', [Validators.maxLength(20)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    usuarioId: [''],
    ativo: [true],
  });

  constructor() {
    if (!this.ehNovo()) {
      this.service.buscar(this.id).subscribe((dados) => {
        this.form.patchValue({
          nome: dados.nome,
          cnh: dados.cnh ?? '',
          telefone: dados.telefone ?? '',
          email: dados.email ?? '',
          usuarioId: dados.usuarioId != null ? String(dados.usuarioId) : '',
          ativo: dados.ativo,
        });
      });
    }
  }

  protected ehNovo(): boolean {
    return !this.id;
  }

  protected invalido(campo: 'nome' | 'email'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  protected mensagem(campo: 'nome' | 'email'): string {
    const erros = this.form.controls[campo].errors ?? {};
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

    const raw = this.form.getRawValue();
    const dados: MotoristaSalvar = {
      nome: raw.nome,
      cnh: raw.cnh || null,
      telefone: raw.telefone || null,
      email: raw.email || null,
      usuarioId: this.paraInteiro(raw.usuarioId),
      ativo: raw.ativo,
    };

    this.salvando.set(true);
    const requisicao = this.ehNovo()
      ? this.service.criar(dados)
      : this.service.atualizar(this.id, dados);

    requisicao.subscribe({
      next: () => {
        this.toast.sucesso(this.ehNovo() ? 'Motorista cadastrado.' : 'Alteracoes salvas.');
        this.router.navigate(['/motoristas']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.aplicarErrosDoServidor(e.error?.errors);
      },
    });
  }

  private paraInteiro(v: string): number | null {
    if (v === '' || v == null) {
      return null;
    }
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : null;
  }

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
