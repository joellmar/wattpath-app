import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { provideRouter } from "@angular/router";
import { TariffStore } from "../../store/tariff.store";
import { TelemetryStore } from "../../store/telemetry.store";
import DashboardComponent from "./dashboard.component";

// Stub mínimo del TariffStore para aislar el componente
function createTariffStoreMock(overrides: { myTariff?: object | null } = {}) {
	const myTariffValue = overrides.myTariff ?? null;
	const myTariffSignal = signal(myTariffValue);
	const hasMyTariffSignal = signal(myTariffValue !== null);

	return {
		myTariff: myTariffSignal.asReadonly(),
		hasMyTariff: hasMyTariffSignal.asReadonly(),
		isLoadingMyTariff: signal(false).asReadonly(),
		errorMessage: signal<string | null>(null).asReadonly(),
		loadMyTariff: () => {},
	};
}

// Stub mínimo del TelemetryStore
function createTelemetryStoreMock() {
	return {
		devices: signal([]).asReadonly(),
		selectedMac: signal<string | null>(null).asReadonly(),
		currentReadings: signal({ timestamps: [], powerW: [] }).asReadonly(),
		isLoadingDevices: signal(false).asReadonly(),
		loadDevices: () => {},
		connectTelemetry: () => {},
		setSelectedMac: () => {},
	};
}

describe("DashboardComponent — integración con TariffStore", () => {
	async function setup(myTariff: object | null = null) {
		const tariffMock = createTariffStoreMock({ myTariff });
		const telemetryMock = createTelemetryStoreMock();

		await TestBed.configureTestingModule({
			imports: [DashboardComponent],
			providers: [
				provideHttpClient(),
				provideHttpClientTesting(),
				provideRouter([]),
				{ provide: TariffStore, useValue: tariffMock },
				{ provide: TelemetryStore, useValue: telemetryMock },
			],
			schemas: [NO_ERRORS_SCHEMA],
		}).compileComponents();

		const fixture = TestBed.createComponent(DashboardComponent);
		fixture.detectChanges();
		return { fixture, component: fixture.componentInstance };
	}

	it("debe instanciarse correctamente", async () => {
		const { component } = await setup();
		expect(component).toBeTruthy();
	});

	it("hasMyTariff es false cuando myTariff es null", async () => {
		const { component } = await setup(null);
		expect(component.hasMyTariff()).toBe(false);
	});

	it("hasMyTariff es true cuando el store devuelve una tarifa", async () => {
		const tariffMock = createTariffStoreMock({
			myTariff: {
				id: 1,
				name: "Mi tarifa",
				market: "libre",
				accessTariffCode: "2.0TD",
				geographicZone: "PENINSULA",
				energyCompany: "Endesa",
				periods: [],
				contractedPowers: [],
			},
		});
		const telemetryMock = createTelemetryStoreMock();

		await TestBed.configureTestingModule({
			imports: [DashboardComponent],
			providers: [
				provideHttpClient(),
				provideHttpClientTesting(),
				provideRouter([]),
				{ provide: TariffStore, useValue: tariffMock },
				{ provide: TelemetryStore, useValue: telemetryMock },
			],
			schemas: [NO_ERRORS_SCHEMA],
		}).compileComponents();

		const fixture = TestBed.createComponent(DashboardComponent);
		fixture.detectChanges();

		expect(fixture.componentInstance.hasMyTariff()).toBe(true);
	});

	it("totalCostEur y ghostCostEur arrancan como null (placeholder -- €)", async () => {
		const { component } = await setup(null);
		expect(component.totalCostEur()).toBeNull();
		expect(component.ghostCostEur()).toBeNull();
	});

	it("sin tarifa el banner de CTA está en el DOM", async () => {
		const { fixture } = await setup(null);
		const compiled = fixture.nativeElement as HTMLElement;
		const bannerText = compiled.textContent ?? "";
		// El banner debe contener el texto del CTA
		expect(bannerText).toContain("configurar");
	});
});
