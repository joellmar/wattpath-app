import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Badge } from "primeng/badge";
import { Button } from "primeng/button";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
import { TableModule } from "primeng/table";
import type { Device } from "../../interfaces/device.interface";
import { TelemetryStore } from "../../store/telemetry.store";

// Interface estricta para el formulario
interface ClaimDeviceForm {
	name: string;
	macAddress: string;
}

@Component({
	selector: "app-devices",
	standalone: true,
	imports: [
		ReactiveFormsModule,
		CommonModule,
		InputText,
		Button,
		Message,
		TableModule,
		Badge,
	],
	templateUrl: "./devices.html",
	styleUrl: "./devices.css",
})
export default class DevicesComponent {
	readonly store = inject(TelemetryStore);
	private readonly formBuilder = inject(FormBuilder).nonNullable;
	private readonly http = inject(HttpClient);

	readonly isLoadingSubmit = signal<boolean>(false);
	readonly errorMessage = signal<string | null>(null);
	readonly successMessage = signal<string | null>(null);

	// Expresión regular estándar para validar direcciones MAC industriales (con o sin dos puntos)
	private readonly macRegex = /^[0-9A-Fa-f]{12}$/;

	readonly deviceForm = this.formBuilder.group({
		name: ["", [Validators.required, Validators.minLength(3)]],
		macAddress: ["", [Validators.required, Validators.pattern(this.macRegex)]],
	});

	constructor() {
		this.store.loadDevices();
	}

	onSubmit(): void {
		if (this.deviceForm.invalid) {
			this.deviceForm.markAllAsTouched();
			return;
		}

		this.isLoadingSubmit.set(true);
		this.errorMessage.set(null);
		this.successMessage.set(null);

		const formValues = this.deviceForm.getRawValue() as ClaimDeviceForm;

		this.http.post<Device>("/api/v1/devices/claim", formValues).subscribe({
			next: () => {
				this.isLoadingSubmit.set(false);
				this.successMessage.set(
					"El dispositivo ha sido registrado y vinculado correctamente a tu cuenta.",
				);
				this.deviceForm.reset();
				this.store.loadDevices(); // Refrescamos el grid de la tabla
			},
			error: (err) => {
				this.isLoadingSubmit.set(false);
				this.errorMessage.set(
					err.error?.message || "Error al añadir o vincular el dispositivo.",
				);
			},
		});
	}

	onDelete(id: number): void {
		if (
			confirm(
				"¿Deseas dar de baja este dispositivo IoT de la red de la empresa?",
			)
		) {
			this.errorMessage.set(null);
			this.successMessage.set(null);

			this.http.delete(`/api/v1/devices/${id}`).subscribe({
				next: () => {
					this.successMessage.set("Dispositivo eliminado de la red.");
					this.store.loadDevices();
				},
				error: () => {
					this.errorMessage.set(
						"No se pudo eliminar el dispositivo de la red.",
					);
				},
			});
		}
	}

	toggleDeviceStatus(device: Device): void {
		this.errorMessage.set(null);
		this.successMessage.set(null);

		// Invoca el método de actualización del CRUD para cambiar el estado virtual (HU-20)
		const payload = {
			...device,
			isOn: !device.isOn,
		};

		this.http.put<Device>(`/api/v1/devices/${device.id}`, payload).subscribe({
			next: () => {
				this.store.loadDevices();
			},
			error: () => {
				this.errorMessage.set(
					"Fallo de comunicación al intentar apagar/encender el dispositivo.",
				);
			},
		});
	}
}
