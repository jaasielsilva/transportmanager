import { DestroyRef, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EstadoComponent } from '../../../shared/estado/estado.component';
import { ToastService } from '../../../core/services/toast.service';
import { CargaResumo } from '../../carga/models/carga.model';
import { RastreamentoService } from '../services/rastreamento.service';

/** Intervalo do POST de posicao enquanto o rastreamento de uma carga esta ativo. */
const INTERVALO_ENVIO_MS = 12_000;

/**
 * Tela do motorista: lista as cargas em transito vinculadas a ele e deixa
 * ligar/desligar o envio de posicao (Geolocation do navegador) por carga.
 *
 * Estado em signal() — a app e zoneless, campo comum nao atualiza a tela.
 */
@Component({
  selector: 'app-minhas-entregas',
  imports: [RouterLink, EstadoComponent],
  template: `
    <div class="barra-topo">
      <div>
        <h1>Minhas entregas</h1>
        <p class="texto-suave">Cargas em transito vinculadas a voce.</p>
      </div>
    </div>

    @if (!suportaGeolocalizacao()) {
      <div class="card" style="margin-bottom: 12px">
        <p class="texto-suave">
          Este navegador nao suporta compartilhamento de localizacao. Abra esta tela pelo
          navegador do celular para rastrear as entregas.
        </p>
      </div>
    }

    <div class="card" style="padding: 0; overflow: hidden">
      @if (carregando() || erro() || !motorista()) {
        <app-estado
          [carregando]="carregando()"
          [erro]="erro()"
          [vazio]="!carregando() && !erro() && !motorista()"
          tituloVazio="Voce nao esta cadastrado como motorista"
          textoVazio="Peca para o administrador da empresa vincular seu login a um motorista."
        />
      } @else if (cargas().length === 0) {
        <app-estado
          [vazio]="true"
          tituloVazio="Nenhuma entrega em transito"
          textoVazio="Assim que uma carga sua entrar em transito, ela aparece aqui."
        />
      } @else {
        <table class="tabela">
          <thead>
            <tr>
              <th>Carga</th>
              <th>Origem</th>
              <th>Destino</th>
              <th style="width: 1%"></th>
            </tr>
          </thead>
          <tbody>
            @for (item of cargas(); track item.id) {
              <tr>
                <td><a [routerLink]="['/cargas', item.id]">{{ item.nome }}</a></td>
                <td>{{ rotuloCidade(item.origemCidade, item.origemUf) }}</td>
                <td>{{ rotuloCidade(item.destinoCidade, item.destinoUf) }}</td>
                <td style="white-space: nowrap">
                  @if (ativos().has(item.id)) {
                    <button type="button" class="btn btn-secundario" (click)="pararRastreamento(item.id)">
                      Parar rastreamento
                    </button>
                  } @else {
                    <button
                      type="button"
                      class="btn"
                      [disabled]="!suportaGeolocalizacao()"
                      (click)="iniciarRastreamento(item.id)"
                    >
                      Iniciar rastreamento
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class MinhasEntregasPage {
  private readonly service = inject(RastreamentoService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly intervalos = new Map<number, ReturnType<typeof setInterval>>();

  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly motorista = signal<{ id: number } | null>(null);
  protected readonly cargas = signal<CargaResumo[]>([]);
  /** ids das cargas com envio de posicao ativo neste navegador agora. */
  protected readonly ativos = signal<Set<number>>(new Set());

  protected readonly suportaGeolocalizacao = signal(
    typeof navigator !== 'undefined' && 'geolocation' in navigator,
  );

  constructor() {
    this.carregar();
    // Para de enviar posicao se a pessoa sair da tela sem clicar em "Parar".
    this.destroyRef.onDestroy(() => this.pararTodos());
  }

  protected rotuloCidade(cidade: string | null, uf: string | null): string {
    if (!cidade) {
      return '—';
    }
    return uf ? `${cidade}/${uf}` : cidade;
  }

  protected iniciarRastreamento(cargaId: number): void {
    if (this.intervalos.has(cargaId)) {
      return;
    }

    const enviarUmaVez = () => {
      navigator.geolocation.getCurrentPosition(
        (posicao) => {
          this.service
            .enviarPosicao(cargaId, posicao.coords.latitude, posicao.coords.longitude)
            .subscribe({ error: () => this.toast.erro('Falha ao enviar posicao. Tentando de novo...') });
        },
        () => this.toast.erro('Nao foi possivel obter sua localizacao. Verifique a permissao do navegador.'),
        { enableHighAccuracy: true, timeout: 10_000 },
      );
    };

    enviarUmaVez();
    const id = setInterval(enviarUmaVez, INTERVALO_ENVIO_MS);
    this.intervalos.set(cargaId, id);
    this.ativos.update((atuais) => new Set(atuais).add(cargaId));
    this.toast.sucesso('Rastreamento iniciado.');
  }

  protected pararRastreamento(cargaId: number): void {
    const id = this.intervalos.get(cargaId);
    if (id != null) {
      clearInterval(id);
      this.intervalos.delete(cargaId);
    }
    this.ativos.update((atuais) => {
      const novo = new Set(atuais);
      novo.delete(cargaId);
      return novo;
    });
  }

  private pararTodos(): void {
    for (const id of this.intervalos.values()) {
      clearInterval(id);
    }
    this.intervalos.clear();
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    this.service.buscarMeuMotorista().subscribe({
      next: (motorista) => {
        if (!motorista) {
          this.motorista.set(null);
          this.carregando.set(false);
          return;
        }
        this.motorista.set({ id: motorista.id });
        this.service.listarMinhasCargasAtivas(motorista.id).subscribe({
          next: (cargas) => {
            this.cargas.set(cargas);
            this.carregando.set(false);
          },
          error: () => {
            this.erro.set('Nao foi possivel carregar suas entregas.');
            this.carregando.set(false);
          },
        });
      },
      error: () => {
        this.erro.set('Nao foi possivel carregar seus dados de motorista.');
        this.carregando.set(false);
      },
    });
  }
}
