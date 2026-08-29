import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/services/toast.service';

/**
 * Molde do kit — features/equipe/equipe.page.ts
 *
 * Convite de usuario pelo TENANT_ADMIN (POST /usuarios/convite).
 *
 * O convidado recebe um link e define a PROPRIA senha: assim nenhuma senha
 * trafega por e-mail, e ninguem (nem o admin, nem nos) conhece a senha de
 * outra pessoa.
 *
 * Estourar a quota MAX_USUARIOS do plano volta como 409 com o caminho de
 * upgrade — o toast ja mostra a mensagem do backend, que e comercial e nao
 * tecnica.
 *
 * PENDENTE no kit: listar e desativar usuarios (o backend ainda nao tem o
 * endpoint de listagem). Nao inventar tela para API que nao existe.
 */
@Component({
  selector: 'app-equipe',
  imports: [ReactiveFormsModule],
  template: `
    <h1>Equipe</h1>
    <p class="texto-suave">Convide quem trabalha com voce. Cada pessoa entra com o proprio acesso.</p>

    <form class="card" style="max-width: 520px; margin-top: 16px" [formGroup]="form" (ngSubmit)="convidar()">
      <h2>Convidar pessoa</h2>

      <label class="campo">
        <span>Nome</span>
        <input formControlName="nome" />
      </label>

      <label class="campo">
        <span>E-mail</span>
        <input type="email" formControlName="email" />
      </label>

      <label class="campo">
        <span>Perfil</span>
        <select formControlName="role">
          <option value="USER">Operacional — usa o dia a dia</option>
          <option value="TENANT_ADMIN">Administrador — gerencia usuarios e plano</option>
        </select>
      </label>

      <button type="submit" class="btn" [disabled]="enviando()">
        {{ enviando() ? 'Enviando...' : 'Enviar convite' }}
      </button>

      <p class="texto-suave" style="margin-bottom: 0">
        O convite expira em 7 dias. A pessoa define a senha ao aceitar.
      </p>
    </form>
  `,
})
export class EquipePage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);

  protected readonly enviando = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    role: ['USER', [Validators.required]],
  });

  protected convidar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.http
      .post(`${environment.apiUrl}/usuarios/convite`, this.form.getRawValue())
      .subscribe({
        next: () => {
          this.toast.sucesso(`Convite enviado para ${this.form.getRawValue().email}.`);
          this.form.reset({ role: 'USER' });
          this.enviando.set(false);
        },
        // 409 (e-mail em uso ou quota do plano) ja virou toast no interceptor.
        error: () => this.enviando.set(false),
      });
  }
}
