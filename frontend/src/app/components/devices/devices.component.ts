import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, effect, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Badge } from "primeng/badge";
import { Button } from "primeng/button";
import { Dialog } from "primeng/dialog";
import { Fluid } from "primeng/fluid";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
// Table no es standalone en PrimeNG 21.1.7; se usa TableModule que sí está disponible en esta instalación
import { TableModule } from "primeng/table";
import type { Device } from "../../interfaces/device.interface";
import { TelemetryStore } from "../../store/telemetry.store";

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
		Dialog,
		Fluid,
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

	readonly selectedDevice = signal<Device | null>(null);
	readonly detailsDialogVisible = signal(false);
	readonly editDialogVisible = signal(false);
	readonly isSavingEdit = signal(false);

	private _successTimer: number | null = null;
	private _errorTimer: number | null = null;

	private readonly macRegex = /^[0-9A-Fa-f]{12}$/;

	readonly deviceForm = this.formBuilder.group({
		name: ["", [Validators.required, Validators.minLength(3)]],
		macAddress: ["", [Validators.required, Validators.pattern(this.macRegex)]],
	});

	readonly editDeviceForm = this.formBuilder.group({
		name: ["", [Validators.required, Validators.minLength(3)]],
	});

	constructor() {
		this.store.loadDevices();

		effect(() => {
			if (this.successMessage() !== null) {
				if (this._successTimer !== null) clearTimeout(this._successTimer);
				this._successTimer = window.setTimeout(
					() => this.successMessage.set(null),
					5000,
				);
			}
		});

		effect(() => {
			if (this.errorMessage() !== null) {
				if (this._errorTimer !== null) clearTimeout(this._errorTimer);
				this._errorTimer = window.setTimeout(
					() => this.errorMessage.set(null),
					7000,
				);
			}
		});
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
				this.store.loadDevices();
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

		const payload: Device = { ...device, isOn: !device.isOn };

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

	openDetails(device: Device): void {
		this.selectedDevice.set(device);
		this.detailsDialogVisible.set(true);
	}

	openEdit(device: Device): void {
		this.selectedDevice.set(device);
		this.editDeviceForm.setValue({ name: device.name });
		this.editDialogVisible.set(true);
	}

	saveDeviceName(): void {
		const device = this.selectedDevice();

		if (device === null || this.editDeviceForm.invalid) {
			this.editDeviceForm.markAllAsTouched();
			return;
		}

		this.isSavingEdit.set(true);
		this.errorMessage.set(null);
		this.successMessage.set(null);

		// Solo se modifica el nombre; el resto de campos del dispositivo se preservan tal cual.
		const payload: Device = {
			...device,
			name: this.editDeviceForm.controls.name.getRawValue().trim(),
		};

		this.http.put<Device>(`/api/v1/devices/${device.id}`, payload).subscribe({
			next: () => {
				this.isSavingEdit.set(false);
				this.editDialogVisible.set(false);
				this.successMessage.set("Nombre del dispositivo actualizado.");
				this.store.loadDevices();
			},
			error: () => {
				this.isSavingEdit.set(false);
				this.errorMessage.set(
					"No se pudo actualizar el nombre del dispositivo.",
				);
			},
		});
	}
}
