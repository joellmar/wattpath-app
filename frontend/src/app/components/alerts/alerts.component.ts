import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, inject, signal } from "@angular/core";
import { Button } from "primeng/button";
import { Message } from "primeng/message";
import { TableModule } from "primeng/table";
import type { Alert } from "../../interfaces/alert.interface";

@Component({
	selector: "app-alerts",
	imports: [CommonModule, Button, Message, TableModule],
	templateUrl: "./alerts.html",
	styleUrl: "./alerts.css",
})
export default class AlertsComponent {
	private readonly http = inject(HttpClient);

	readonly alertsList = signal<Alert[]>([]);
	readonly isLoading = signal<boolean>(false);
	readonly errorMessage = signal<string | null>(null);
	readonly successMessage = signal<string | null>(null);

	constructor() {
		this.loadAlerts();
	}

	loadAlerts(): void {
		this.isLoading.set(true);
		this.errorMessage.set(null);

		this.http.get<Alert[]>("/api/v1/alerts").subscribe({
			next: (data) => {
				this.isLoading.set(false);
				this.alertsList.set(data);
			},
			error: () => {
				this.isLoading.set(false);
				this.errorMessage.set(
					"No se ha podido recuperar el historial de alertas.",
				);
			},
		});
	}

	onDismissAlert(id: number): void {
		this.errorMessage.set(null);
		this.successMessage.set(null);

		// Llama al endpoint de borrado de alertas
		this.http.delete(`/api/v1/alerts/${id}`).subscribe({
			next: () => {
				this.successMessage.set(
					"Incidencia de maxímetro descartada correctamente.",
				);
				this.loadAlerts(); // Refresca la tabla
			},
			error: () => {
				this.errorMessage.set(
					"Error de autorización: no se pudo descartar la alerta.",
				);
			},
		});
	}
}
