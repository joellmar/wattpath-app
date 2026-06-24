import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, effect, inject, signal } from "@angular/core";
import {
	FormBuilder,
	ReactiveFormsModule,
	type ValidatorFn,
	Validators,
} from "@angular/forms";
import { Badge } from "primeng/badge";
import { Button } from "primeng/button";
import { Dialog } from "primeng/dialog";
import { Fluid } from "primeng/fluid";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
import { Select } from "primeng/select";
import { SelectButton } from "primeng/selectbutton";
import { TableModule } from "primeng/table";
import type { Device } from "../../interfaces/device.interface";
import type {
	DeviceKind,
	SimulationProfile,
} from "../../interfaces/simulation-profile.interface";
import {
	SIMULATION_PROFILE_OPTIONS,
	simulationProfileLabel,
} from "../../interfaces/simulation-profile.interface";
import { TelemetryStore } from "../../store/telemetry.store";

interface DeviceKindOption {
	label: string;
	value: DeviceKind;
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
		Select,
		SelectButton,
	],
	templateUrl: "./devices.html",
	styleUrl: "./devices.css",
})
export default class DevicesComponent {
	readonly store = inject(TelemetryStore);
	private readonly formBuilder = inject(FormBuilder).nonNullable;
	private readonly http = inject(HttpClient);

	readonly isLoadingSubmit = signal<boolean>(false);
	readonly isLoadingDemoPack = signal<boolean>(false);
	readonly errorMessage = signal<string | null>(null);
	readonly successMessage = signal<string | null>(null);

	readonly selectedDevice = signal<Device | null>(null);
	readonly detailsDialogVisible = signal(false);
	readonly editDialogVisible = signal(false);
	readonly isSavingEdit = signal(false);

	readonly simulationProfileOptions = SIMULATION_PROFILE_OPTIONS;
	readonly deviceKindOptions: DeviceKindOption[] = [
		{ label: "Físico", value: "physical" },
		{ label: "Simulado", value: "simulated" },
	];
	readonly simulationProfileLabel = simulationProfileLabel;

	private _successTimer: number | null = null;
	private _errorTimer: number | null = null;

	private readonly macRegex = /^[0-9A-Fa-f]{12}$/;

	readonly deviceForm = this.formBuilder.group({
		deviceKind: this.formBuilder.control<DeviceKind>("physical", {
			validators: [Validators.required],
		}),
		name: ["", [Validators.required, Validators.minLength(3)]],
		macAddress: ["", [Validators.required, Validators.pattern(this.macRegex)]],
		simulationProfile: this.formBuilder.control<SimulationProfile | null>(null),
	});

	readonly editDeviceForm = this.formBuilder.group({
		name: ["", [Validators.required, Validators.minLength(3)]],
		simulationProfile: this.formBuilder.control<SimulationProfile | null>(null),
	});

	constructor() {
		this.store.loadDevices();
		this.applyDeviceKindValidators("physical");

		this.deviceForm.controls.deviceKind.valueChanges.subscribe((kind) => {
			if (kind != null) {
				this.applyDeviceKindValidators(kind);
			}
		});

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

		const formValues = this.deviceForm.getRawValue();
		const isSimulated = formValues.deviceKind === "simulated";

		if (isSimulated) {
			const profile = formValues.simulationProfile;
			if (profile == null) {
				this.isLoadingSubmit.set(false);
				this.errorMessage.set(
					"Selecciona un perfil de consumo para el simulador.",
				);
				return;
			}

			this.http
				.post<Device>("/api/v1/devices/simulated", {
					name: formValues.name.trim(),
					simulationProfile: profile,
				})
				.subscribe({
					next: () => {
						this.isLoadingSubmit.set(false);
						this.successMessage.set(
							"El simulador se ha creado y ya está generando lecturas.",
						);
						this.resetCreateForm();
						this.store.loadDevices();
					},
					error: (err) => {
						this.isLoadingSubmit.set(false);
						this.errorMessage.set(
							err.error?.message || "No se pudo crear el dispositivo simulado.",
						);
					},
				});
			return;
		}

		this.http
			.post<Device>("/api/v1/devices/claim", {
				name: formValues.name.trim(),
				macAddress: formValues.macAddress.trim(),
			})
			.subscribe({
				next: () => {
					this.isLoadingSubmit.set(false);
					this.successMessage.set(
						"El dispositivo ha sido registrado y vinculado correctamente a tu cuenta.",
					);
					this.resetCreateForm();
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

	addDemoPack(): void {
		this.isLoadingDemoPack.set(true);
		this.errorMessage.set(null);
		this.successMessage.set(null);

		this.http
			.post<Device[]>("/api/v1/devices/simulated/demo-pack", {})
			.subscribe({
				next: (createdDevices) => {
					this.isLoadingDemoPack.set(false);
					if (createdDevices.length === 0) {
						this.successMessage.set(
							"Ya tienes todos los perfiles de demostración en tu cuenta.",
						);
					} else {
						this.successMessage.set(
							`Se han añadido ${createdDevices.length} simuladores. Ya puedes probarlos en el panel.`,
						);
					}
					this.store.loadDevices();
				},
				error: (err) => {
					this.isLoadingDemoPack.set(false);
					this.errorMessage.set(
						err.error?.message || "No se pudo crear el pack de demostración.",
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
				error: (err: { status?: number; error?: { message?: string } }) => {
					this.errorMessage.set(
						err.error?.message ||
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
					"Fallo de comunicación al intentar apagar o encender el dispositivo.",
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
		this.editDeviceForm.setValue({
			name: device.name,
			simulationProfile: device.simulationProfile,
		});
		this.editDialogVisible.set(true);
	}

	saveDeviceEdit(): void {
		const device = this.selectedDevice();

		if (device === null || this.editDeviceForm.invalid) {
			this.editDeviceForm.markAllAsTouched();
			return;
		}

		this.isSavingEdit.set(true);
		this.errorMessage.set(null);
		this.successMessage.set(null);

		const payload: Device = {
			...device,
			name: this.editDeviceForm.controls.name.getRawValue().trim(),
			simulationProfile: device.simulated
				? this.editDeviceForm.controls.simulationProfile.getRawValue()
				: device.simulationProfile,
		};

		this.http.put<Device>(`/api/v1/devices/${device.id}`, payload).subscribe({
			next: () => {
				this.isSavingEdit.set(false);
				this.editDialogVisible.set(false);
				this.successMessage.set("Dispositivo actualizado correctamente.");
				this.store.loadDevices();
			},
			error: () => {
				this.isSavingEdit.set(false);
				this.errorMessage.set("No se pudo actualizar el dispositivo.");
			},
		});
	}

	isSimulatedForm(): boolean {
		return this.deviceForm.controls.deviceKind.getRawValue() === "simulated";
	}

	private resetCreateForm(): void {
		this.deviceForm.reset({
			deviceKind: "physical",
			name: "",
			macAddress: "",
			simulationProfile: null,
		});
		this.applyDeviceKindValidators("physical");
	}

	private applyDeviceKindValidators(kind: DeviceKind): void {
		const macControl = this.deviceForm.controls.macAddress;
		const profileControl = this.deviceForm.controls.simulationProfile;

		if (kind === "simulated") {
			macControl.clearValidators();
			macControl.setValue("");
			macControl.disable({ emitEvent: false });

			profileControl.setValidators([Validators.required]);
			if (profileControl.getRawValue() == null) {
				profileControl.setValue("SINE_WAVE");
			}
			profileControl.enable({ emitEvent: false });
		} else {
			macControl.setValidators([
				Validators.required,
				Validators.pattern(this.macRegex),
			] as ValidatorFn[]);
			macControl.enable({ emitEvent: false });

			profileControl.clearValidators();
			profileControl.setValue(null);
			profileControl.disable({ emitEvent: false });
		}

		macControl.updateValueAndValidity({ emitEvent: false });
		profileControl.updateValueAndValidity({ emitEvent: false });
	}
}
