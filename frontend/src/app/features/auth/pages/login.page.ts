import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

/**
 * Molde do kit — features/auth/pages/login.page.ts
 *
 * PAGINA DE REFERENCIA de formulario: Reactive Forms, erro por campo exibido
 * so depois de `touched`, botao desabilitado durante o envio.
 *
 * O botao desabilitado nao e detalhe: sem ele, dois cliques viram duas
 * requisicoes de login, duas rotacoes de refresh token e uma sessao derrubada
 * por "reuso de token" — um bug que ninguem consegue reproduzir de proposito.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="tela-auth">
      <div>
        <div class="marca">TransportManager</div>
        <form class="card caixa" [formGroup]="form" (ngSubmit)="entrar()">
          <h2>Entrar</h2>

          <label class="campo">
            <span>E-mail</span>
            <input
              type="email"
              formControlName="email"
              autocomplete="username"
              [class.invalido]="invalido('email')"
            />
            @if (invalido('email')) {
              <span class="erro-campo">Informe um e-mail valido.</span>
            }
          </label>

          <label class="campo">
            <span>Senha</span>
            <input
              type="password"
              formControlName="senha"
              autocomplete="current-password"
              [class.invalido]="invalido('senha')"
            />
            @if (invalido('senha')) {
              <span class="erro-campo">Informe sua senha.</span>
            }
          </label>

          @if (erro()) {
            <p class="erro-campo">{{ erro() }}</p>
          }

          <button type="submit" class="btn" style="width: 100%" [disabled]="enviando()">
            {{ enviando() ? 'Entrando...' : 'Entrar' }}
          </button>

          <p class="rodape">
            <a routerLink="/esqueci-senha">Esqueci minha senha</a>
          </p>
          <p class="rodape texto-suave">
            Ainda nao tem conta? <a routerLink="/criar-conta">Criar conta gratis</a>
          </p>
        </form>
      </div>
    </div>
  `,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', [Validators.required]],
  });

  protected invalido(campo: 'email' | 'senha'): boolean {
    const controle = this.form.controls[campo];
    return controle.invalid && controle.touched;
  }

  protected entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.erro.set(null);
    const { email, senha } = this.form.getRawValue();

    this.auth.login(email, senha).subscribe({
      next: () => {
        // Volta para a pagina que a pessoa tentou abrir antes de logar.
        const destino = this.rota.snapshot.queryParamMap.get('returnUrl') ?? '/inicio';
        this.router.navigateByUrl(destino);
      },
      error: (e) => {
        this.enviando.set(false);
        // Mensagem unica para e-mail inexistente e senha errada — o backend ja
        // responde assim de proposito, para nao entregar quem tem conta.
        this.erro.set(e.error?.message ?? 'Nao foi possivel entrar. Tente novamente.');
      },
    });
  }
}
