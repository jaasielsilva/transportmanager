import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Molde do kit — features/conta/pages/alterar-senha.page.ts
 *
 * Troca da propria senha, para quem JA esta logado. Sem esta tela o unico
 * caminho e sair e usar "esqueci minha senha", ou seja, precisar do e-mail
 * para trocar uma senha que a pessoa sabe — e quem perdeu o acesso ao e-mail
 * fica sem saida a nao ser UPDATE na mao no banco de producao.
 *
 * Nao confundir com DefinirSenhaPage (publica, vale por token de e-mail, para
 * quem NAO consegue entrar).
 *
 * Sem assinaturaGuard na rota, de proposito: o backend isenta /api/v1/me do
 * AssinaturaAccessInterceptor, e prender a troca de senha atras do pagamento
 * em dia seria prender a reacao a um vazamento atras da fatura.
 */
@Component({
  selector: 'app-alterar-senha',
  imports: [ReactiveFormsModule],
  template: `
    <div class="barra-topo">
      <h1>Minha conta</h1>
    </div>

    <div class="corpo-form">
      <form class="card" [formGroup]="form" (ngSubmit)="salvar()">
        <h2 style="margin-top: 0">Alterar senha</h2>
        <p class="texto-suave">
          Ao salvar, todas as sessões abertas são encerradas — inclusive esta. Você
          entra de novo com a senha nova.
        </p>

        <label class="campo">
          <span>Senha atual *</span>
          <input
            type="password"
            formControlName="senhaAtual"
            autocomplete="current-password"
            [class.invalido]="invalido('senhaAtual')"
          />
          @if (invalido('senhaAtual')) {
            <span class="erro-campo">Informe a senha atual.</span>
          }
        </label>

        <label class="campo">
          <span>Nova senha *</span>
          <input
            type="password"
            formControlName="novaSenha"
            autocomplete="new-password"
            [class.invalido]="invalido('novaSenha')"
          />
          @if (invalido('novaSenha')) {
            <span class="erro-campo">Mínimo de 8 caracteres.</span>
          } @else {
            <span class="texto-suave">Mínimo de 8 caracteres.</span>
          }
        </label>

        <label class="campo">
          <span>Repita a nova senha *</span>
          <input type="password" formControlName="repeticao" autocomplete="new-password" />
          @if (naoConfere()) {
            <span class="erro-campo">As senhas não são iguais.</span>
          }
        </label>

        <div style="display: flex; justify-content: flex-end; margin-top: 8px">
          <button type="submit" class="btn" [disabled]="salvando()">
            {{ salvando() ? 'Salvando...' : 'Alterar senha' }}
          </button>
        </div>
      </form>

      <aside class="card painel-preview">
        <h3 style="margin-top: 0">Seus dados</h3>
        <p class="texto-suave" style="margin-bottom: 4px">Nome</p>
        <p style="margin-top: 0">{{ usuario()?.nome }}</p>
        <p class="texto-suave" style="margin-bottom: 4px">E-mail de acesso</p>
        <p style="margin-top: 0">{{ usuario()?.email }}</p>
        <p class="texto-suave" style="margin-bottom: 4px">Empresa</p>
        <p style="margin-top: 0">{{ usuario()?.empresa }}</p>
      </aside>
    </div>
  `,
})
export class AlterarSenhaPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  protected readonly usuario = this.auth.usuario;
  protected readonly salvando = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    senhaAtual: ['', [Validators.required]],
    novaSenha: ['', [Validators.required, Validators.minLength(8)]],
    repeticao: ['', [Validators.required]],
  });

  protected invalido(campo: 'senhaAtual' | 'novaSenha'): boolean {
    const c = this.form.controls[campo];
    return c.touched && c.invalid;
  }

  protected naoConfere(): boolean {
    const { novaSenha, repeticao } = this.form.getRawValue();
    return this.form.controls.repeticao.touched && novaSenha !== repeticao;
  }

  protected salvar(): void {
    if (this.form.invalid || this.naoConfere()) {
      this.form.markAllAsTouched();
      return;
    }

    const { senhaAtual, novaSenha } = this.form.getRawValue();
    this.salvando.set(true);

    this.http.post(`${environment.apiUrl}/me/senha`, { senhaAtual, novaSenha }).subscribe({
      next: () => {
        this.toast.sucesso('Senha alterada. Entre novamente com a nova senha.');
        // O backend ja revogou os refresh tokens; o access token em memoria
        // ainda valeria ate expirar, entao a sessao local tem que cair aqui.
        // O logout pode falhar (o cookie de refresh acabou de ser revogado) —
        // sair da tela nao pode depender disso.
        this.auth.logout().subscribe({
          next: () => this.router.navigate(['/login']),
          error: () => this.router.navigate(['/login']),
        });
      },
      // Mensagem de erro (senha atual incorreta) ja vem do backend pelo
      // interceptor de erro; aqui so libera o botao.
      error: () => this.salvando.set(false),
    });
  }
}
