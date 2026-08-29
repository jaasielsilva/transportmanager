import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Molde do kit — features/auth/pages/definir-senha.page.ts
 *
 * Atende os DOIS fluxos que terminam em "escolha sua senha": redefinicao
 * (esqueci) e aceite de convite. Sao a mesma tela e a mesma regra; o que muda
 * e o endpoint, entregue por `data.modo` na rota.
 *
 * O convidado define a propria senha justamente para que nenhuma senha
 * trafegue por e-mail — o e-mail leva so um token de uso unico e expiravel.
 */
@Component({
  selector: 'app-definir-senha',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="tela-auth">
      <div>
        <div class="marca">TransportManager</div>
        <div class="card caixa">
          @if (!token) {
            <h2>Link invalido</h2>
            <p class="texto-suave">
              O link esta incompleto ou expirou. Solicite um novo para continuar.
            </p>
            <p class="rodape"><a routerLink="/login">Voltar para o login</a></p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="salvar()">
              <h2>{{ ehConvite ? 'Concluir cadastro' : 'Nova senha' }}</h2>

              <label class="campo">
                <span>Senha</span>
                <input type="password" formControlName="senha" autocomplete="new-password" />
                <span class="texto-suave">Minimo de 8 caracteres.</span>
              </label>

              <label class="campo">
                <span>Repita a senha</span>
                <input type="password" formControlName="repeticao" autocomplete="new-password" />
                @if (naoConfere()) {
                  <span class="erro-campo">As senhas nao sao iguais.</span>
                }
              </label>

              <button type="submit" class="btn" style="width: 100%" [disabled]="enviando()">
                {{ enviando() ? 'Salvando...' : 'Salvar senha' }}
              </button>
            </form>
          }
        </div>
      </div>
    </div>
  `,
})
export class DefinirSenhaPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  protected readonly token = this.rota.snapshot.queryParamMap.get('token');
  protected readonly ehConvite = this.rota.snapshot.data['modo'] === 'convite';

  protected readonly enviando = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    senha: ['', [Validators.required, Validators.minLength(8)]],
    repeticao: ['', [Validators.required]],
  });

  protected naoConfere(): boolean {
    const { senha, repeticao } = this.form.getRawValue();
    return this.form.controls.repeticao.touched && senha !== repeticao;
  }

  protected salvar(): void {
    if (this.form.invalid || this.naoConfere() || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    const { senha } = this.form.getRawValue();
    const url = this.ehConvite
      ? `${environment.apiUrl}/auth/aceitar-convite`
      : `${environment.apiUrl}/auth/redefinir-senha`;
    // Os dois endpoints tem contratos proprios; nao unifique no backend so
    // porque a tela e a mesma — sao ciclos de vida diferentes.
    const corpo = this.ehConvite
      ? { token: this.token, senha }
      : { token: this.token, novaSenha: senha };

    this.enviando.set(true);
    this.http.post(url, corpo).subscribe({
      next: () => {
        this.toast.sucesso('Senha definida. Faca login para entrar.');
        this.router.navigate(['/login']);
      },
      error: () => this.enviando.set(false),
    });
  }
}
