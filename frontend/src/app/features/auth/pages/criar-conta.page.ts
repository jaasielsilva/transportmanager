import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Molde do kit — features/auth/pages/criar-conta.page.ts
 *
 * Signup self-service: cria empresa + primeiro TENANT_ADMIN em uma transacao
 * (POST /auth/signup) e ja abre o trial.
 *
 * Esta e a porta de entrada comercial do produto. Cada campo a mais aqui
 * derruba conversao — por isso o formulario pede o minimo para existir um
 * tenant, e nada mais. O resto se pergunta depois, dentro do produto.
 */
@Component({
  selector: 'app-criar-conta',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="tela-auth">
      <div>
        <div class="marca">TransportManager</div>
        <form class="card caixa" [formGroup]="form" (ngSubmit)="criar()">
          <h2>Criar conta</h2>
          <p class="texto-suave">Teste gratuito, sem cartao de credito.</p>

          <label class="campo">
            <span>Nome da empresa</span>
            <input formControlName="razaoSocial" [class.invalido]="invalido('razaoSocial')" />
          </label>

          <label class="campo">
            <span>CNPJ ou CPF</span>
            <input formControlName="documento" [class.invalido]="invalido('documento')" />
          </label>

          <label class="campo">
            <span>Seu nome</span>
            <input formControlName="nomeAdmin" [class.invalido]="invalido('nomeAdmin')" />
          </label>

          <label class="campo">
            <span>Seu e-mail</span>
            <input type="email" formControlName="email" [class.invalido]="invalido('email')" />
          </label>

          <label class="campo">
            <span>Senha</span>
            <input
              type="password"
              formControlName="senha"
              autocomplete="new-password"
              [class.invalido]="invalido('senha')"
            />
            <span class="texto-suave">Minimo de 8 caracteres.</span>
          </label>

          <button type="submit" class="btn" style="width: 100%" [disabled]="enviando()">
            {{ enviando() ? 'Criando...' : 'Criar conta' }}
          </button>

          <p class="rodape texto-suave">Ja tem conta? <a routerLink="/login">Entrar</a></p>
        </form>
      </div>
    </div>
  `,
})
export class CriarContaPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly enviando = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    razaoSocial: ['', [Validators.required, Validators.maxLength(150)]],
    documento: ['', [Validators.required, Validators.maxLength(20)]],
    nomeAdmin: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    senha: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected invalido(
    campo: 'razaoSocial' | 'documento' | 'nomeAdmin' | 'email' | 'senha',
  ): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  protected criar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.http.post(`${environment.apiUrl}/auth/signup`, this.form.getRawValue()).subscribe({
      next: () => {
        this.toast.sucesso('Conta criada. Faca login para comecar.');
        this.router.navigate(['/login']);
      },
      // O erro de duplicidade (409) ja vira toast no errorInterceptor.
      error: () => this.enviando.set(false),
    });
  }
}
