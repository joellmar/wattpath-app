import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ChartModule } from "primeng/chart";
import { Select } from "primeng/select";
import { FormsModule } from '@angular/forms';
import { TelemetryStore } from '../../store/telemetry.store';
import { CommonModule } from '@angular/common';
import { Button } from 'primeng/button';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

const options: Intl.DateTimeFormatOptions = {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  };

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ChartModule,
    Select,
    Button
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
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

  private readonly historicalData = this.store.currentReadings;
  readonly powerW = computed(() => this.historicalData().powerW);
  private readonly timestamps = computed(() => this.historicalData().timestamps);

  readonly companyName = computed(() => {
    const defaultName = "Administrador";
    const list = this.devices();

    if (list.length > 0) {
      const username = list[0].username;
      if (username && username.includes("@")) {
        return username.split("@")[0];
      }
    }

    return defaultName;
  });

  private readonly formatedTime = computed(() =>
    this.timestamps().map(ts =>
      new Date(ts).toLocaleTimeString("es-ES", options)
    )
  );

  chartData = computed(() => ({
    labels: this.formatedTime(), // Eje X (El tiempo)
    datasets: [
      {
        label: "Consumo activo (W)", // Eje Y
        data: this.powerW(), // Valores de potencia
        fill: false,
        borderColor: '#10b981',
        tension: 0.4
      }
    ]
  }));

  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        labels: { color: '#e2e8f0' }
      }
    },
    scales: {
      x: { grid: { color: '#334155' }, ticks: { color: '#94a3b8' } },
      y: { grid: { color: '#334155' }, ticks: { color: '#94a3b8' } }
    }
  };

  constructor() {
    // Cargamos los datos iniciales
    this.store.loadDevices();
    // Conectamos el consumidor del WebSocket al Signal de la MAC seleccionada
    this.store.connectTelemetry(this.store.selectedMac);
    this.checkTariffStatus();
  }

  // Consulta síncrona de tarifas al iniciar para desbloquear estadísticas
  private checkTariffStatus(): void {
    this.http.get<any[]>("/api/v1/tariffs").subscribe({
      next: (tariffs) => {
        this.hasTariff.set(tariffs && tariffs.length > 0);
      },
      error: (err) => {
        this.hasTariff.set(false);
        console.error("Fallo al verificar el estado de las tarifas corporativas.", err);
      }
    });
  }

  navigateToTariffConfig(): void {
    this.router.navigate(["/tariffs"]);
  }
}
