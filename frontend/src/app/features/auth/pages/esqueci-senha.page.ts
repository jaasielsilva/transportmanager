import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';

/**
 * Molde do kit — features/auth/pages/esqueci-senha.page.ts
 *
 * A tela responde SEMPRE a mesma coisa, exista ou nao o e-mail. Confirmar que
 * "esse e-mail nao esta cadastrado" transforma a tela num verificador de quem
 * tem conta no sistema — informacao que vale ouro para quem monta ataque.
 */
@Component({
  selector: 'app-esqueci-senha',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="tela-auth">
      <div>
        <div class="marca">TransportManager</div>
        <div class="card caixa">
          @if (enviado()) {
            <h2>Verifique seu e-mail</h2>
            <p class="texto-suave">
              Se o e-mail estiver cadastrado, enviamos as instrucoes para redefinir a senha. O link
              vale por 30 minutos.
            </p>
            <p class="rodape"><a routerLink="/login">Voltar para o login</a></p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="enviar()">
              <h2>Recuperar senha</h2>
              <p class="texto-suave">Enviaremos um link para voce criar uma nova senha.</p>

              <label class="campo">
                <span>E-mail</span>
                <input type="email" formControlName="email" />
              </label>

              <button type="submit" class="btn" style="width: 100%" [disabled]="enviando()">
                {{ enviando() ? 'Enviando...' : 'Enviar link' }}
              </button>
              <p class="rodape"><a routerLink="/login">Voltar</a></p>
            </form>
          }
        </div>
      </div>
    </div>
  `,
})
export class EsqueciSenhaPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);

  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.http.post(`${environment.apiUrl}/auth/esqueci-senha`, this.form.getRawValue()).subscribe({
      // Mesma tela de sucesso nos dois casos — inclusive quando o e-mail nao existe.
      next: () => this.enviado.set(true),
      error: () => this.enviando.set(false),
    });
  }
}
