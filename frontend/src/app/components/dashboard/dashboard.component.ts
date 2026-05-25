import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ChartModule } from "primeng/chart";
import { Select } from "primeng/select";
import { FormsModule } from '@angular/forms';
import { TelemetryStore } from '../../store/telemetry.store';
import { CommonModule } from '@angular/common';
import { Button } from 'primeng/button';
import { Router } from '@angular/router';

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

  // Mapeamos propiedades directas a las del Store
  readonly devices = this.store.devices;
  // Simulamos la comprobación de tarifa para el flujo UX del MVP.
  // En el futuro, esto leerá del perfil de usuario del Store.
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
  }

  navigateToTariffConfig(): void {
    this.router.navigate(["/tariffs"]);
  }
}
