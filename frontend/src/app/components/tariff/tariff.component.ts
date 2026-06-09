import { DecimalPipe, NgTemplateOutlet } from "@angular/common";
import { Component, computed, effect, inject, signal } from "@angular/core";
import {
	type AbstractControl,
	type FormArray,
	FormBuilder,
	type FormControl,
	type FormGroup,
	ReactiveFormsModule,
	type ValidationErrors,
	Validators,
} from "@angular/forms";
import { Button } from "primeng/button";
import { InputNumber } from "primeng/inputnumber";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
import { Select } from "primeng/select";
import type {
	AccessTariffCode,
	PeriodCode,
	TariffRequest,
	TariffResponse,
} from "../../interfaces/tariff-request.interface";
import { SessionStorageService } from "../../services/session-storage.service";
import { TariffService } from "../../services/tariff.service";
import { TariffStore } from "../../store/tariff.store";

interface PeriodFormGroup {
	id: FormControl<number | null>;
	periodCode: FormControl<PeriodCode>;
	priceKwh: FormControl<number>;
}

interface ContractedPowerFormGroup {
	id: FormControl<number | null>;
	periodCode: FormControl<PeriodCode>;
	contractedPowerKw: FormControl<number>;
}

// Definición normativa de periodos por peaje según Circular CNMC 3/2020
const ENERGY_PERIODS: Record<AccessTariffCode, PeriodCode[]> = {
	"2.0TD": ["P1", "P2", "P3"],
	"3.0TD": ["P1", "P2", "P3", "P4", "P5", "P6"],
	"6.1TD": ["P1", "P2", "P3", "P4", "P5", "P6"],
	"6.2TD": ["P1", "P2", "P3", "P4", "P5", "P6"],
};

const POWER_PERIODS: Record<AccessTariffCode, PeriodCode[]> = {
	"2.0TD": ["P1", "P2"],
	"3.0TD": ["P1", "P2", "P3", "P4", "P5", "P6"],
	"6.1TD": ["P1", "P2", "P3", "P4", "P5", "P6"],
	"6.2TD": ["P1", "P2", "P3", "P4", "P5", "P6"],
};

// Impone P1 <= P2 <= ... <= P6 en el FormArray de potencias contratadas
function ascendingPowerValidator(
	control: AbstractControl,
): ValidationErrors | null {
	const arr = control as FormArray;
	if (arr.length < 2) return null;
	const values = arr.controls.map(
		(g) => (g as FormGroup).get("contractedPowerKw")?.value ?? 0,
	);
	for (let i = 1; i < values.length; i++) {
		if (values[i] < values[i - 1]) {
			return { ascendingPower: true };
		}
	}
	return null;
}

@Component({
	selector: "app-tariff",
	standalone: true,
	imports: [
		DecimalPipe,
		NgTemplateOutlet,
		ReactiveFormsModule,
		InputText,
		InputNumber,
		Select,
		Button,
		Message,
	],
	templateUrl: "./tariff.html",
})
export default class TariffComponent {
	private readonly fb = inject(FormBuilder).nonNullable;
	private readonly tariffService = inject(TariffService);
	readonly sessionService = inject(SessionStorageService);
	readonly store = inject(TariffStore);

	// ── Estado de rol ─────────────────────────────────────────────────────────
	readonly isAdmin = computed(() => this.sessionService.hasRole("ROLE_ADMIN"));

	// ── Estado de UI local ────────────────────────────────────────────────────
	readonly isSubmitting = signal<boolean>(false);
	readonly successMessage = signal<string | null>(null);
	readonly errorMessage = signal<string | null>(null);

	private _successTimer: number | null = null;
	private _errorTimer: number | null = null;
	// null = sin formulario abierto; truthy = tarifa que se está editando (o nueva)
	readonly showForm = signal<boolean>(false);
	readonly editingTariff = signal<TariffResponse | null>(null);

	// Verdadero cuando el formulario abierto corresponde a la tarifa privada del usuario.
	// Se distingue del formulario de catálogo mirando si la tarifa editada coincide con myTariff.
	readonly isEditingMyTariffMode = computed(
		() =>
			this.editingTariff() !== null &&
			this.editingTariff()?.id === this.store.myTariff()?.id,
	);

	// ── Opciones de selectores ────────────────────────────────────────────────
	readonly accessTariffOptions = [
		{ label: "2.0TD — Doméstica (hasta 15 kW)", value: "2.0TD" },
		{ label: "3.0TD — Baja tensión (> 15 kW)", value: "3.0TD" },
		{ label: "6.1TD — Alta tensión NT1 (1–30 kV)", value: "6.1TD" },
		{ label: "6.2TD — Alta tensión NT2 (30–72,5 kV)", value: "6.2TD" },
	];

	readonly marketOptions = [
		{ label: "Mercado libre", value: "libre" },
		{ label: "Mercado regulado (PVPC)", value: "regulado" },
	];

	readonly geographicZoneOptions = [
		{ label: "Península", value: "PENINSULA" },
		{ label: "Islas Baleares", value: "ISLAS_BALEARES" },
		{ label: "Canarias", value: "CANARIAS" },
		{ label: "Ceuta", value: "CEUTA" },
		{ label: "Melilla", value: "MELILLA" },
	];

	// ── Formulario reactivo tipado ────────────────────────────────────────────
	readonly tariffForm = this.fb.group({
		id: this.fb.control<number | null>(null),
		name: ["", [Validators.required, Validators.minLength(3)]],
		market: ["libre", Validators.required],
		accessTariffCode: this.fb.control<AccessTariffCode>(
			"2.0TD",
			Validators.required,
		),
		geographicZone: this.fb.control("PENINSULA", Validators.required),
		energyCompany: ["", Validators.required],
		periods: this.fb.array<FormGroup<PeriodFormGroup>>([], Validators.required),
		contractedPowers: this.fb.array<FormGroup<ContractedPowerFormGroup>>(
			[],
			[Validators.required, ascendingPowerValidator],
		),
	});

	constructor() {
		this.store.loadCatalog();
		this.store.loadMyTariff();

		// Propaga errorMessage del store al estado local de UI
		effect(() => {
			const storeError = this.store.errorMessage();
			if (storeError) {
				this.errorMessage.set(storeError);
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

		// Reconstruye los arrays al cambiar el peaje de acceso seleccionado en el form
		this.tariffForm.controls.accessTariffCode.valueChanges.subscribe((code) => {
			if (code) this.rebuildPeriodArrays(code as AccessTariffCode);
		});

		this.rebuildPeriodArrays("2.0TD");
	}

	// ── Getters tipados de FormArray ──────────────────────────────────────────

	get periods(): FormArray<FormGroup<PeriodFormGroup>> {
		return this.tariffForm.controls.periods;
	}

	get contractedPowers(): FormArray<FormGroup<ContractedPowerFormGroup>> {
		return this.tariffForm.controls.contractedPowers;
	}

	// ── Reconstrucción de arrays de periodos ──────────────────────────────────

	rebuildPeriodArrays(code: AccessTariffCode): void {
		this.periods.clear();
		this.contractedPowers.clear();

		for (const pc of ENERGY_PERIODS[code]) {
			this.periods.push(this.createPeriodGroup(pc));
		}
		for (const pc of POWER_PERIODS[code]) {
			this.contractedPowers.push(this.createContractedPowerGroup(pc));
		}
	}

	private createPeriodGroup(
		periodCode: PeriodCode,
		id: number | null = null,
		priceKwh = 0.15,
	): FormGroup<PeriodFormGroup> {
		return this.fb.group<PeriodFormGroup>({
			id: this.fb.control<number | null>(id),
			periodCode: this.fb.control<PeriodCode>(periodCode, Validators.required),
			priceKwh: this.fb.control(priceKwh, [
				Validators.required,
				Validators.min(0.0001),
			]),
		});
	}

	private createContractedPowerGroup(
		periodCode: PeriodCode,
		id: number | null = null,
		contractedPowerKw = 5.75,
	): FormGroup<ContractedPowerFormGroup> {
		return this.fb.group<ContractedPowerFormGroup>({
			id: this.fb.control<number | null>(id),
			periodCode: this.fb.control<PeriodCode>(periodCode, Validators.required),
			contractedPowerKw: this.fb.control(contractedPowerKw, [
				Validators.required,
				Validators.min(0.001),
			]),
		});
	}

	private loadTariffIntoForm(tariff: TariffResponse): void {
		this.periods.clear();
		this.contractedPowers.clear();

		for (const p of tariff.periods) {
			this.periods.push(
				this.createPeriodGroup(p.periodCode as PeriodCode, p.id, p.priceKwh),
			);
		}
		for (const cp of tariff.contractedPowers) {
			this.contractedPowers.push(
				this.createContractedPowerGroup(
					cp.periodCode as PeriodCode,
					cp.id,
					cp.contractedPowerKw,
				),
			);
		}

		// { emitEvent: false } impide que el cambio de accessTariffCode
		// dispare la suscripción valueChanges, que reconstruiría los arrays
		// con valores por defecto borrando los precios reales que acabamos de cargar.
		this.tariffForm.patchValue(
			{
				id: tariff.id,
				name: tariff.name,
				market: tariff.market,
				accessTariffCode: tariff.accessTariffCode,
				geographicZone: tariff.geographicZone,
				energyCompany: tariff.energyCompany,
			},
			{ emitEvent: false },
		);
	}

	private resetFormMessages(): void {
		this.errorMessage.set(null);
		this.successMessage.set(null);
		this.store.clearError();
	}

	// Bloquea los campos estructurales de la tarifa para que el usuario solo pueda
	// editar los precios de energía y las potencias contratadas.
	// { emitEvent: false } es obligatorio: enable() y disable() emiten valueChanges
	// por defecto, lo que dispararía rebuildPeriodArrays y resetearía los arrays
	// de periodos a los valores por defecto justo después de cargar los datos reales.
	private disableStructuralFields(): void {
		this.tariffForm.controls.name.disable({ emitEvent: false });
		this.tariffForm.controls.market.disable({ emitEvent: false });
		this.tariffForm.controls.accessTariffCode.disable({ emitEvent: false });
		this.tariffForm.controls.geographicZone.disable({ emitEvent: false });
		this.tariffForm.controls.energyCompany.disable({ emitEvent: false });
	}

	private enableStructuralFields(): void {
		this.tariffForm.controls.name.enable({ emitEvent: false });
		this.tariffForm.controls.market.enable({ emitEvent: false });
		this.tariffForm.controls.accessTariffCode.enable({ emitEvent: false });
		this.tariffForm.controls.geographicZone.enable({ emitEvent: false });
		this.tariffForm.controls.energyCompany.enable({ emitEvent: false });
	}

	// ── ADMIN: catálogo maestro ───────────────────────────────────────────────

	onNewCatalogTariff(): void {
		this.editingTariff.set(null);
		this.showForm.set(true);
		this.tariffForm.reset({
			market: "libre",
			accessTariffCode: "2.0TD",
			geographicZone: "PENINSULA",
		});
		this.rebuildPeriodArrays("2.0TD");
		this.enableStructuralFields();
		this.resetFormMessages();
	}

	onEditCatalogTariff(tariff: TariffResponse): void {
		this.editingTariff.set(tariff);
		this.showForm.set(true);
		this.loadTariffIntoForm(tariff);
		this.enableStructuralFields();
		this.resetFormMessages();
	}

	onCancelForm(): void {
		this.showForm.set(false);
		this.editingTariff.set(null);
		this.enableStructuralFields();
		this.resetFormMessages();
	}

	onSubmitCatalogForm(): void {
		if (this.tariffForm.invalid) {
			this.tariffForm.markAllAsTouched();
			return;
		}

		this.isSubmitting.set(true);
		this.resetFormMessages();

		const raw = this.tariffForm.getRawValue() as TariffRequest;
		const editing = this.editingTariff();

		const request$ = editing?.id
			? this.tariffService.updateCatalogTariff(editing.id, raw)
			: this.tariffService.createCatalogTariff(raw);

		request$.subscribe({
			next: (saved) => {
				this.isSubmitting.set(false);
				if (editing?.id) {
					this.store.setCatalogTariff(saved);
					this.successMessage.set("Plantilla actualizada en el catálogo.");
				} else {
					this.store.addToCatalog(saved);
					this.successMessage.set("Nueva plantilla añadida al catálogo.");
				}
				this.showForm.set(false);
				this.editingTariff.set(null);
				this.enableStructuralFields();
			},
			error: (err) => {
				this.isSubmitting.set(false);
				this.errorMessage.set(
					err?.error?.message ??
						"No se ha podido guardar la plantilla. Revisa los datos e inténtalo de nuevo.",
				);
			},
		});
	}

	onDeleteCatalogTariff(tariff: TariffResponse): void {
		if (!tariff.id) return;
		if (
			!confirm(
				`¿Eliminar "${tariff.name}" del catálogo? Esta acción no puede deshacerse.`,
			)
		)
			return;

		this.tariffService.deleteCatalogTariff(tariff.id).subscribe({
			next: () => {
				this.store.removeFromCatalog(tariff.id as number);
				this.successMessage.set(`Plantilla "${tariff.name}" eliminada.`);
				if (this.editingTariff()?.id === tariff.id) this.showForm.set(false);
			},
			error: (err) => {
				this.errorMessage.set(
					err?.error?.message ??
						"No se ha podido eliminar la plantilla del catálogo.",
				);
			},
		});
	}

	// ── Tarifa privada (compartido entre admin y user) ────────────────────────

	onAssignTemplate(tariff: TariffResponse): void {
		if (!tariff.id) return;
		this.resetFormMessages();
		this.store.saveMyTariff({ templateTariffId: tariff.id, contract: null });
	}

	onEditMyTariff(): void {
		const myTariff = this.store.myTariff();
		if (!myTariff) return;
		this.editingTariff.set(myTariff);
		this.showForm.set(true);
		this.loadTariffIntoForm(myTariff);
		// Solo se pueden editar precios y potencias; los campos estructurales se bloquean
		this.disableStructuralFields();
		this.resetFormMessages();
	}

	onSubmitMyTariff(): void {
		if (this.tariffForm.invalid) {
			this.tariffForm.markAllAsTouched();
			return;
		}

		this.isSubmitting.set(true);
		this.resetFormMessages();

		const raw = this.tariffForm.getRawValue() as TariffRequest;

		// Llama directamente al servicio (no al store) para tener callbacks next/error
		// y evitar mostrar el toast de éxito antes de confirmar la respuesta del servidor.
		this.tariffService
			.saveMyTariff({ templateTariffId: null, contract: raw })
			.subscribe({
				next: (saved) => {
					this.isSubmitting.set(false);
					this.store.patchMyTariff(saved);
					this.showForm.set(false);
					this.editingTariff.set(null);
					this.enableStructuralFields();
					this.successMessage.set("Precios de tu contrato actualizados.");
				},
				error: (err) => {
					this.isSubmitting.set(false);
					this.errorMessage.set(
						err?.error?.message ??
							"No se han podido guardar los cambios. Revisa los precios e inténtalo de nuevo.",
					);
				},
			});
	}

	// Selecciona todo el texto del InputNumber al hacer foco para que el usuario
	// pueda reemplazar el valor con una pulsación de tecla, sin tener que borrarlo antes.
	onNumberInputFocus(event: unknown): void {
		const input = (event as FocusEvent)?.target as HTMLInputElement | null;
		input?.select();
	}

	onUnlinkMyTariff(): void {
		if (
			!confirm(
				"¿Desvincular tu tarifa? Las métricas de costes quedarán desactivadas hasta que asignes una nueva.",
			)
		)
			return;
		this.store.unlinkMyTariff();
		this.showForm.set(false);
		this.editingTariff.set(null);
		this.enableStructuralFields();
		this.successMessage.set("Tarifa desvinculada.");
	}
}
