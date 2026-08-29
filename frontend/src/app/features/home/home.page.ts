import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CargaService } from '../carga/services/carga.service';

/**
 * Molde do kit — features/home/home.page.ts
 *
 * Primeira tela depois do login. NAO e um dashboard de graficos: e o checklist
 * de ativacao.
 *
 * Motivo, direto: trial que abre numa tela vazia nao converte. A pessoa entra,
 * nao sabe o que fazer primeiro e nao volta. Tres passos claros valem mais que
 * dez indicadores bonitos — e os indicadores so existem depois que ela cadastra
 * alguma coisa.
 *
 * O progresso aqui e DERIVADO do que a API ja responde. Persistir o checklist
 * no tenant (e medir a taxa de ativacao no painel /platform) e o passo
 * seguinte, e esta marcado como pendente na ARQUITETURA.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <h1>Ola, {{ primeiroNome() }}</h1>
    <p class="texto-suave">
      {{ usuario()?.empresa }} —
      {{ emTrial() ? 'periodo de teste' : 'assinatura ' + (usuario()?.assinaturaStatus ?? '') }}
    </p>

    <div class="card" style="margin-top: 16px; max-width: 640px">
      <h2>Primeiros passos</h2>

      <ol class="passos">
        @if (temModuloCadastros()) {
          <li [class.feito]="temCadastro()">
            <div>
              <strong>Cadastre o primeiro carga</strong>
              <p class="texto-suave">E o que faz o sistema deixar de estar vazio.</p>
            </div>
            @if (!temCadastro()) {
              <a class="btn" routerLink="/cargas/novo">Cadastrar</a>
            } @else {
              <span class="selo selo-ativo">feito</span>
            }
          </li>
        }

        @if (ehAdmin()) {
          <li>
            <div>
              <strong>Convide sua equipe</strong>
              <p class="texto-suave">Cada pessoa entra com o proprio acesso.</p>
            </div>
            <a class="btn btn-secundario" routerLink="/equipe">Convidar</a>
          </li>

          <li>
            <div>
              <strong>Confira seu plano</strong>
              <p class="texto-suave">Veja o que esta incluido e ate quando vai o teste.</p>
            </div>
            <a class="btn btn-secundario" routerLink="/plano">Ver plano</a>
          </li>
        }
      </ol>
    </div>
  `,
  styles: [
    `
      .passos {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .passos li {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 14px;
        border: 1px solid var(--borda);
        border-radius: var(--raio-pequeno);
      }
      .passos li > div {
        flex: 1;
      }
      .passos p {
        margin: 2px 0 0;
      }
      .passos li.feito {
        border-color: #a7f3d0;
        background: #f0fdf4;
      }
    `,
  ],
})
export class HomePage {
  private readonly auth = inject(AuthService);
  private readonly service = inject(CargaService);

  protected readonly usuario = this.auth.usuario;
  protected readonly temCadastro = signal(false);

  protected readonly primeiroNome = computed(() => this.usuario()?.nome.split(' ')[0] ?? '');
  protected readonly emTrial = computed(() => this.usuario()?.assinaturaStatus === 'TRIALING');
  protected readonly ehAdmin = computed(() => this.auth.temRole('TENANT_ADMIN'));
  protected readonly temModuloCadastros = computed(() => this.auth.temModulo('CADASTROS'));

  constructor() {
    // So chama a API se o plano incluir o modulo: sem esta guarda, a home de
    // quem nao tem CADASTROS abriria com um toast de 403 — o pior cartao de
    // visita possivel para a tela inicial.
    if (this.temModuloCadastros()) {
      // Uma pagina de 1 registro so para saber se existe algum: o total vem no
      // envelope de paginacao, sem trazer a lista inteira para a home.
      this.service
        .listar('', 0, 1)
        .subscribe((pagina) => this.temCadastro.set(pagina.totalElements > 0));
    }
  }
}
