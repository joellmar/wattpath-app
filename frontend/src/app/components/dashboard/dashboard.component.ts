import { DecimalPipe } from "@angular/common";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, computed, effect, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { MessageService } from "primeng/api";
import { Button } from "primeng/button";
import { ChartModule } from "primeng/chart";
import { Message } from "primeng/message";
import { Select } from "primeng/select";
import { ToastModule } from "primeng/toast";
import type { EnergyCostResponse } from "../../interfaces/energy-cost-response.interface";
import type { GhostCostResponse } from "../../interfaces/ghost-cost-response.interface";
import { TariffStore } from "../../store/tariff.store";
import { TelemetryStore } from "../../store/telemetry.store";

const TIME_OPTIONS: Intl.DateTimeFormatOptions = {
	hour: "2-digit",
	minute: "2-digit",
	second: "2-digit",
	hour12: false,
};

@Component({
	selector: "app-dashboard",
	standalone: true,
	imports: [
		DecimalPipe,
		FormsModule,
		ChartModule,
		Select,
		Button,
		Message,
		ToastModule,
	],
	providers: [MessageService],
	templateUrl: "./dashboard.html",
	styleUrl: "./dashboard.css",
})
export default class DashboardComponent {
	readonly store = inject(TelemetryStore);
	readonly tariffStore = inject(TariffStore);
	private readonly router = inject(Router);
	private readonly http = inject(HttpClient);

	// Delegamos a TariffStore en vez de consultar GET /api/v1/tariffs
	readonly hasMyTariff = this.tariffStore.hasMyTariff;

	readonly devices = this.store.devices;

	// null = sin datos todavía; muestra placeholder "-- €"
	readonly totalCostEur = signal<number | null>(null);
	readonly ghostCostEur = signal<number | null>(null);
	// Indica si el widget de analytics está cargando
	readonly isLoadingAnalytics = signal<boolean>(false);
	readonly analyticsError = signal<string | null>(null);

	private _analyticsErrorTimer: number | null = null;

	private readonly historicalData = this.store.currentReadings;
	readonly powerW = computed(() => this.historicalData().powerW);
	private readonly timestamps = computed(
		() => this.historicalData().timestamps,
	);

	readonly companyName = computed(() => {
		const list = this.devices();
		if (list.length > 0) {
			const username = list[0].username;
			if (username?.includes("@")) {
				return username.split("@")[0];
			}
		}
		return "Administrador";
	});

	readonly formattedTime = computed(() =>
		this.timestamps().map((ts) =>
			new Date(ts).toLocaleTimeString("es-ES", TIME_OPTIONS),
		),
	);

	readonly chartData = computed(() => ({
		labels: this.formattedTime(),
		datasets: [
			{
				label: "Consumo activo (W)",
				data: this.powerW(),
				fill: true,
				// Área bajo la curva con transparencia para resaltar el volumen de consumo
				backgroundColor: "rgba(16,185,129,0.07)",
				borderColor: "#10b981",
				borderWidth: 2,
				pointBackgroundColor: "#10b981",
				pointBorderColor: "#0f172a",
				pointBorderWidth: 2,
				pointRadius: 4,
				pointHoverRadius: 7,
				tension: 0.4,
			},
		],
	}));

	readonly chartOptions = {
		responsive: true,
		maintainAspectRatio: false,
		animation: { duration: 250 },
		// Tooltip unificado en la posición X más cercana al cursor
		interaction: { intersect: false, mode: "index" as const },
		plugins: {
			legend: {
				labels: {
					color: "#cbd5e1",
					usePointStyle: true,
					pointStyle: "circle" as const,
					padding: 20,
					font: { size: 12 },
				},
			},
			tooltip: {
				backgroundColor: "#0f172a",
				titleColor: "#94a3b8",
				bodyColor: "#f1f5f9",
				borderColor: "#10b981",
				borderWidth: 1,
				padding: 12,
				callbacks: {
					label: (ctx: { parsed: { y: number } }) =>
						`  ${ctx.parsed.y.toFixed(1)} W`,
				},
			},
		},
		scales: {
			x: {
				grid: { color: "rgba(51,65,85,0.5)" },
				ticks: {
					color: "#64748b",
					font: { size: 11 },
					maxRotation: 45,
					autoSkip: true,
					maxTicksLimit: 10,
				},
			},
			y: {
				// El eje Y arranca siempre en 0; suggestedMax da margen visual razonable
				// sin que un pico puntual del broker destruya la escala de toda la sesión.
				min: 0,
				suggestedMax: 500,
				grid: { color: "rgba(51,65,85,0.5)" },
				ticks: {
					color: "#64748b",
					font: { size: 11 },
					callback: (val: number | string) => `${val} W`,
				},
				title: {
					display: true,
					text: "Potencia (W)",
					color: "#475569",
					font: { size: 11 },
				},
			},
		},
	};

	constructor() {
		this.store.loadDevices();
		this.tariffStore.loadMyTariff();

		effect(() => {
			if (this.analyticsError() !== null) {
				if (this._analyticsErrorTimer !== null) clearTimeout(this._analyticsErrorTimer);
				this._analyticsErrorTimer = window.setTimeout(() => this.analyticsError.set(null), 8000);
			}
		});

		effect(() => {
			const mac = this.store.selectedMac();
			if (mac) {
				this.store.connectTelemetry(mac);
				// Solo lanzamos analíticas si hay tarifa configurada y MAC seleccionada
				if (this.hasMyTariff()) {
					this.loadAnalyticsMetrics(mac);
				}
			}
		});

		// Cuando el usuario configura la tarifa desde el banner, recargamos analíticas
		effect(() => {
			const hasTariff = this.hasMyTariff();
			const mac = this.store.selectedMac();

			if (!hasTariff) {
				// Sin tarifa: reseteamos métricas a null para mostrar placeholders
				this.totalCostEur.set(null);
				this.ghostCostEur.set(null);
				this.analyticsError.set(null);
			} else if (mac) {
				this.loadAnalyticsMetrics(mac);
			}
		});
	}

	private loadAnalyticsMetrics(macAddress: string): void {
		if (!macAddress || !this.hasMyTariff()) return;

		this.isLoadingAnalytics.set(true);
		this.analyticsError.set(null);

		const now = new Date();
		const startOfToday = new Date(
			now.getFullYear(),
			now.getMonth(),
			now.getDate(),
			0,
			0,
			0,
			0,
		);

		const params = new HttpParams()
			.set("macAddress", macAddress)
			.set("start", startOfToday.toISOString())
			.set("end", now.toISOString());

		this.http
			.get<EnergyCostResponse>("/api/v1/analytics/cost", { params })
			.subscribe({
				next: (res) => {
					if (res?.totalCostEur !== undefined) {
						this.totalCostEur.set(res.totalCostEur);
					}
					this.isLoadingAnalytics.set(false);
				},
				error: (err) => {
					this.isLoadingAnalytics.set(false);
					this.analyticsError.set(
						"No se han podido calcular los costes. Inténtalo de nuevo más tarde.",
					);
					console.error("Error al recuperar el coste financiero real.", err);
				},
			});

		this.http
			.get<GhostCostResponse>("/api/v1/analytics/ghost-consumption", { params })
			.subscribe({
				next: (res) => {
					if (res?.ghostCostEur !== undefined) {
						this.ghostCostEur.set(res.ghostCostEur);
					}
				},
				error: (err) => {
					console.error("Error al recuperar el consumo fantasma.", err);
				},
			});
	}

	navigateToTariffConfig(): void {
		this.router.navigate(["/tariffs"]);
	}
}
