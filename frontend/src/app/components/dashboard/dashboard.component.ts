import { CommonModule } from "@angular/common";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, computed, effect, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { MessageService } from "primeng/api";
import { Button } from "primeng/button";
import { ChartModule } from "primeng/chart";
import { Select } from "primeng/select";
import { ToastModule } from "primeng/toast";
import type { EnergyCostResponse } from "../../interfaces/energy-cost-response.interface";
import type { GhostCostResponse } from "../../interfaces/ghost-cost-response.interface";
import type { TariffRequest } from "../../interfaces/tariff-request.interface";
import { TelemetryStore } from "../../store/telemetry.store";

const options: Intl.DateTimeFormatOptions = {
	hour: "2-digit",
	minute: "2-digit",
	hour12: false,
};

@Component({
	selector: "app-dashboard",
	standalone: true,
	imports: [
		CommonModule,
		FormsModule,
		ChartModule,
		Select,
		Button,
		ToastModule,
	],
	providers: [MessageService],
	templateUrl: "./dashboard.html",
	styleUrl: "./dashboard.css",
})
export default class DashboardComponent {
	// Inyectamos el almacén global
	readonly store = inject(TelemetryStore);
	private readonly router = inject(Router);
	private readonly http = inject(HttpClient);

	// Mapeamos propiedades directas a las del Store
	readonly devices = this.store.devices;

	// CORRECCIÓN MVP: Ahora es una señal reactiva real que lee del backend
	readonly hasTariff = signal<boolean>(false);

	// Señales de analítica real mapeadas con los métodos del ConsumptionService
	readonly totalCostEur = signal<number>(0.0);
	readonly ghostCostEur = signal<number>(0.0);

	private readonly historicalData = this.store.currentReadings;
	readonly powerW = computed(() => this.historicalData().powerW);
	private readonly timestamps = computed(
		() => this.historicalData().timestamps,
	);

	readonly companyName = computed(() => {
		const defaultName = "Administrador";
		const list = this.devices();

		if (list.length > 0) {
			const username = list[0].username;
			if (username?.includes("@")) {
				return username.split("@")[0];
			}
		}

		return defaultName;
	});

	private readonly formatedTime = computed(() =>
		this.timestamps().map((ts) =>
			new Date(ts).toLocaleTimeString("es-ES", options),
		),
	);

	chartData = computed(() => ({
		labels: this.formatedTime(), // Eje X (El tiempo)
		datasets: [
			{
				label: "Consumo activo (W)", // Eje Y
				data: this.powerW(), // Valores de potencia
				fill: false,
				borderColor: "#10b981",
				tension: 0.4,
			},
		],
	}));

	chartOptions = {
		responsive: true,
		maintainAspectRatio: false,
		plugins: {
			legend: {
				labels: { color: "#e2e8f0" },
			},
		},
		scales: {
			x: { grid: { color: "#334155" }, ticks: { color: "#94a3b8" } },
			y: { grid: { color: "#334155" }, ticks: { color: "#94a3b8" } },
		},
	};

	constructor() {
		// Cargamos los datos iniciales
		this.store.loadDevices();
		this.checkTariffStatus();

		effect(() => {
			const mac = this.store.selectedMac();
			if (mac) {
				this.store.connectTelemetry(mac);
				this.loadAnalyticsMetrics(mac);
			}
		});
	}

	// Consulta síncrona de tarifas al iniciar para desbloquear estadísticas
	private checkTariffStatus(): void {
		this.http.get<TariffRequest[]>("/api/v1/tariffs").subscribe({
			next: (tariffs) => {
				this.hasTariff.set(tariffs && tariffs.length > 0);
			},
			error: (err) => {
				this.hasTariff.set(false);
				console.error(
					"Fallo al verificar el estado de las tarifas corporativas.",
					err,
				);
			},
		});
	}

	// Carga los datos reales consultando tu ConsumptionController
	loadAnalyticsMetrics(macAddress: string): void {
		if (!macAddress) return;

		// Calculamos el rango de hoy: desde las 00:00:00 UTC hasta el instante actual
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

		// Petición 1: Coste diario acumulado (Regla 5)
		this.http
			.get<EnergyCostResponse>("/api/v1/analytics/cost", { params })
			.subscribe({
				next: (res) => {
					if (res && res.totalCostEur !== undefined) {
						this.totalCostEur.set(res.totalCostEur);
					}
				},
				error: (err) =>
					console.error("error al recuperar el coste financiero real.", err),
			});

		// Petición 2: Consumo fantasma en franja de madrugada (Regla 5)
		this.http
			.get<GhostCostResponse>("/api/v1/analytics/ghost-consumption", { params })
			.subscribe({
				next: (res) => {
					if (res && res.ghostCostEur !== undefined) {
						this.ghostCostEur.set(res.ghostCostEur);
					}
				},
				error: (err) =>
					console.error(
						"error al recuperar el indicador de consumo fantasma.",
						err,
					),
			});
	}

	navigateToTariffConfig(): void {
		this.router.navigate(["/tariffs"]);
	}
}
