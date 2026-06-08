import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, inject, signal } from "@angular/core";
import {
	type FormArray,
	FormBuilder,
	type FormControl,
	type FormGroup,
	ReactiveFormsModule,
	Validators,
} from "@angular/forms";
import { Button } from "primeng/button";
import { InputNumber } from "primeng/inputnumber";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
import { Select } from "primeng/select";
import type { TariffRequest } from "../../interfaces/tariff-request.interface";

interface PeriodFormGroup {
	id: FormControl<number | null>;
	name: FormControl<string>;
	priceKwh: FormControl<number>;
	startHour: FormControl<string>;
	endHour: FormControl<string>;
	dayType: FormControl<string>;
	startMonth: FormControl<number>;
	endMonth: FormControl<number>;
}

@Component({
	selector: "app-tariff",
	standalone: true,
	imports: [
		ReactiveFormsModule,
		CommonModule,
		InputText,
		InputNumber,
		Select,
		Button,
		Message,
	],
	templateUrl: "./tariff.html",
	styleUrl: "./tariff.css",
})
export default class TariffComponent {
	private readonly formBuilder = inject(FormBuilder).nonNullable;
	private readonly http = inject(HttpClient);

	readonly isLoading = signal<boolean>(false);
	readonly errorMessage = signal<string | null>(null);
	readonly successMessage = signal<string | null>(null);

	// Almacena la tarifa activa de la cuenta para la lógica del MVP
	readonly activeTariff = signal<TariffRequest | null>(null);

	readonly marketOptions = [
		{ label: "Mercado libre", value: "libre" },
		{ label: "Mercado regulado (PVPC)", value: "regulado" },
	];

	readonly typeOptions = [
		{ label: "Tarifa doméstica (2.0TD)", value: "2.0TD" },
		{ label: "Tarifa industrial de baja tensión (3.0TD)", value: "3.0TD" },
		{ label: "Tarifa de alta tensión (6.1TD)", value: "6.1TD" },
	];

	readonly tariffForm = this.formBuilder.group({
		name: ["", [Validators.required, Validators.minLength(3)]],
		type: ["3.0TD", [Validators.required]],
		market: ["libre", [Validators.required]],
		contractedPowerKw: [15.0, [Validators.required, Validators.min(0.1)]],
		energyCompany: ["", [Validators.required]],
		periods: this.formBuilder.array<FormGroup<PeriodFormGroup>>([]),
	});

	constructor() {
		this.initPeriods();
		this.checkExistingTariff();
	}

	get periods(): FormArray<FormGroup<PeriodFormGroup>> {
		return this.tariffForm.get("periods") as FormArray<
			FormGroup<PeriodFormGroup>
		>;
	}

	private initPeriods(): void {
		const defaultPeriods = [
			{ name: "P1 Punta", startHour: "08:00:00", endHour: "16:00:00" },
			{ name: "P2 Llano", startHour: "16:00:00", endHour: "23:59:59" }, // CRÍTICO: Solucionado el '24:00:00' que rompía el backend
			{ name: "P3 Valle", startHour: "00:00:00", endHour: "08:00:00" },
		];

		defaultPeriods.forEach((p) => {
			const periodGroup = this.formBuilder.group<PeriodFormGroup>({
				id: this.formBuilder.control<number | null>(null),
				name: this.formBuilder.control(p.name, Validators.required),
				priceKwh: this.formBuilder.control(0.15, [
					Validators.required,
					Validators.min(0.0001),
				]),
				startHour: this.formBuilder.control(p.startHour, Validators.required),
				endHour: this.formBuilder.control(p.endHour, Validators.required),
				dayType: this.formBuilder.control("WEEKDAY", Validators.required),
				startMonth: this.formBuilder.control(1, [
					Validators.required,
					Validators.min(1),
					Validators.max(12),
				]),
				endMonth: this.formBuilder.control(12, [
					Validators.required,
					Validators.min(1),
					Validators.max(12),
				]),
			});

			this.periods.push(periodGroup);
		});
	}

	checkExistingTariff(): void {
		this.http.get<TariffRequest[]>("/api/v1/tariffs").subscribe({
			next: (tariffs) => {
				if (tariffs && tariffs.length > 0) {
					// Si ya existe al menos una tarifa, la seteamos como activa para la UX del MVP
					this.activeTariff.set(tariffs[0]);
				} else {
					this.activeTariff.set(null);
				}
			},
			error: () => {
				this.errorMessage.set(
					"No se ha podido comprobar el estado de las tarifas en el servidor.",
				);
			},
		});
	}

	onSubmit(): void {
		if (this.tariffForm.invalid) {
			this.tariffForm.markAllAsTouched();
			return;
		}

		this.isLoading.set(true);
		this.errorMessage.set(null);
		this.successMessage.set(null);

		const payload: TariffRequest =
			this.tariffForm.getRawValue() as TariffRequest;

		this.http.post<TariffRequest>("/api/v1/tariffs", payload).subscribe({
			next: (savedTariff) => {
				this.isLoading.set(false);
				this.successMessage.set(
					"Los costes de los tramos horarios se han registrado con éxito.",
				);
				this.activeTariff.set(savedTariff);
				this.tariffForm.reset({
					type: "3.0TD",
					market: "libre",
					contractedPowerKw: 15.0,
					name: "",
					energyCompany: "",
				});
			},
			error: (err) => {
				this.isLoading.set(false);
				this.errorMessage.set(
					err.error?.message ||
						"Error interno en la plataforma al guardar la tarifa.",
				);
			},
		});
	}

	onDeleteActiveTariff(): void {
		const tariff = this.activeTariff();
		if (!tariff) return;

		if (
			confirm(
				"¿Seguro que deseas eliminar la tarifa activa? Las métricas de costes energéticos se bloquearán de inmediato.",
			)
		) {
			this.errorMessage.set(null);
			this.successMessage.set(null);

			this.http.delete(`/api/v1/tariffs/${tariff.id}`).subscribe({
				next: () => {
					this.successMessage.set(
						"Tarifa eliminada correctamente. Ya puedes configurar una nueva.",
					);
					this.activeTariff.set(null);
				},
				error: () => {
					this.errorMessage.set("No se pudo dar de baja la tarifa actual.");
				},
			});
		}
	}
}
