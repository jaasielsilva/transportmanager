import { DestroyRef, Component, ElementRef, inject, input, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { CargaService } from '../services/carga.service';
import { RastreamentoService } from '../../rastreamento/services/rastreamento.service';

/** Consulta a posicao atual do motorista a cada ~8s enquanto a carga esta em transito. */
const INTERVALO_POSICAO_MS = 8_000;

/**
 * Mapa pequeno com origem, destino e a rota tracada entre eles (Leaflet +
 * OpenStreetMap). Quando a carga esta EM_TRANSITO, tambem mostra o marcador
 * do motorista, atualizado por polling.
 *
 * Standalone, sem servico proprio de mapa: e simples o bastante para viver
 * inteiro aqui, e assim o carga-form so injeta um componente, sem estado
 * extra para gerenciar.
 */
@Component({
  selector: 'app-rota-mapa',
  template: `<div #mapa style="height: 320px; border-radius: var(--raio-pequeno); overflow: hidden"></div>`,
})
export class RotaMapaComponent {
  readonly cargaId = input.required<number>();
  readonly status = input<string | null>(null);

  private readonly cargaService = inject(CargaService);
  private readonly rastreamentoService = inject(RastreamentoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elementoMapa = viewChild.required<ElementRef<HTMLDivElement>>('mapa');

  private mapa?: L.Map;
  private marcadorMotorista?: L.Marker;
  private intervalo?: ReturnType<typeof setInterval>;

  constructor() {
    // afterNextRender nao e obrigatorio aqui porque o template so tem este
    // elemento e o construtor roda apos a view ser criada nos componentes
    // standalone com viewChild.required; ainda assim, adiar para o proximo
    // ciclo de leitura evita depender de timing do Angular.
    queueMicrotask(() => this.inicializar());

    this.destroyRef.onDestroy(() => {
      if (this.intervalo) {
        clearInterval(this.intervalo);
      }
      this.mapa?.remove();
    });
  }

  private inicializar(): void {
    this.mapa = L.map(this.elementoMapa().nativeElement).setView([-14.235, -51.9253], 4);
    // CARTO Voyager em vez do tile padrao do OSM: gratuito, sem chave, e com
    // visual mais limpo e colorido (ruas destacadas), parecido com o Google
    // Maps — o cru do OSM padrao fica com aparencia mais "antiga"/mapa de papel.
    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; OpenStreetMap &copy; CARTO',
      maxZoom: 20,
      subdomains: 'abcd',
    }).addTo(this.mapa);

    // O container pode nascer com tamanho ainda nao calculado pelo layout do
    // Angular (ex.: dentro de um @if) — sem isso o Leaflet as vezes desenha
    // so um pedaco dos tiles ate a janela ser redimensionada.
    setTimeout(() => this.mapa?.invalidateSize(), 0);

    this.cargaService.rotaMapa(this.cargaId()).subscribe({
      next: (mapa) => this.desenharRota(mapa),
      error: () => {
        // Sem rota tracavel (enderecos incompletos, provedor fora do ar): o
        // mapa continua no zoom mundial, sem quebrar a tela de detalhe.
      },
    });

    if (this.status() === 'EM_TRANSITO') {
      this.iniciarPollingDePosicao();
    }
  }

  /**
   * Circulos coloridos em vez de L.marker(): o icone padrao do Leaflet
   * referencia arquivos de imagem (marker-icon.png) que o build do Angular
   * nao copia sem configuracao extra de assets — vira um quadrado quebrado na
   * tela. Circulo via SVG/canvas nao depende de nenhum arquivo.
   */
  private desenharRota(mapa: { origem: { lat: number; lng: number } | null; destino: { lat: number; lng: number } | null; geometria: [number, number][] }): void {
    if (!this.mapa || !mapa.origem || !mapa.destino) {
      return;
    }

    L.circleMarker([mapa.origem.lat, mapa.origem.lng], {
      radius: 8,
      color: '#16a34a',
      fillColor: '#16a34a',
      fillOpacity: 1,
    })
      .addTo(this.mapa)
      .bindPopup('Origem');

    L.circleMarker([mapa.destino.lat, mapa.destino.lng], {
      radius: 8,
      color: '#dc2626',
      fillColor: '#dc2626',
      fillOpacity: 1,
    })
      .addTo(this.mapa)
      .bindPopup('Destino');

    if (mapa.geometria.length > 1) {
      const linha = L.polyline(mapa.geometria, { color: '#4f46e5', weight: 4 }).addTo(this.mapa);
      this.mapa.fitBounds(linha.getBounds(), { padding: [24, 24] });
    } else {
      this.mapa.fitBounds(
        L.latLngBounds([mapa.origem.lat, mapa.origem.lng], [mapa.destino.lat, mapa.destino.lng]),
        { padding: [24, 24] },
      );
    }
    setTimeout(() => this.mapa?.invalidateSize(), 0);
  }

  private iniciarPollingDePosicao(): void {
    const atualizar = () => {
      this.rastreamentoService.posicaoAtual(this.cargaId()).subscribe((posicao) => {
        if (!posicao || !this.mapa) {
          return;
        }
        const posicaoLatLng: L.LatLngExpression = [posicao.latitude, posicao.longitude];
        if (this.marcadorMotorista) {
          this.marcadorMotorista.setLatLng(posicaoLatLng);
        } else {
          this.marcadorMotorista = L.marker(posicaoLatLng, {
            icon: L.divIcon({
              className: 'marcador-motorista',
              html: '<div style="font-size: 20px; line-height: 24px; text-align: center">🚚</div>',
              iconSize: [24, 24],
              iconAnchor: [12, 12],
            }),
          })
            .addTo(this.mapa)
            .bindPopup('Motorista');
        }
      });
    };

    atualizar();
    this.intervalo = setInterval(atualizar, INTERVALO_POSICAO_MS);
  }
}
