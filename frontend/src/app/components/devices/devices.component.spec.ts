import { provideHttpClient } from "@angular/common/http";
import {
	HttpTestingController,
	provideHttpClientTesting,
} from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, signal } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { TelemetryStore } from "../../store/telemetry.store";
import DevicesComponent from "./devices.component";

function createTelemetryStoreMock() {
	return {
		devices: signal([]).asReadonly(),
		selectedMac: signal<string | null>(null).asReadonly(),
		isLoadingDevices: signal(false).asReadonly(),
		loadDevices: () => {},
	};
}

describe("DevicesComponent — alta física y simulada", () => {
	let httpMock: HttpTestingController;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [DevicesComponent],
			providers: [
				provideHttpClient(),
				provideHttpClientTesting(),
				{ provide: TelemetryStore, useValue: createTelemetryStoreMock() },
			],
			schemas: [NO_ERRORS_SCHEMA],
		}).compileComponents();

		httpMock = TestBed.inject(HttpTestingController);
	});

	afterEach(() => {
		httpMock.verify();
	});

	it("exige MAC para dispositivos físicos", () => {
		const fixture = TestBed.createComponent(DevicesComponent);
		const component = fixture.componentInstance;

		component.deviceForm.setValue({
			deviceKind: "physical",
			name: "Medidor nave",
			macAddress: "",
			simulationProfile: null,
		});

		component.onSubmit();

		expect(component.deviceForm.controls.macAddress.invalid).toBe(true);
		httpMock.expectNone("/api/v1/devices/claim");
		httpMock.expectNone("/api/v1/devices/simulated");
	});

	it("exige perfil para dispositivos simulados", () => {
		const fixture = TestBed.createComponent(DevicesComponent);
		const component = fixture.componentInstance;

		component.deviceForm.setValue({
			deviceKind: "simulated",
			name: "Simulador horno",
			macAddress: "",
			simulationProfile: null,
		});
		component.deviceForm.controls.simulationProfile.setValidators([]);
		component.deviceForm.controls.simulationProfile.updateValueAndValidity();

		component.onSubmit();

		expect(component.errorMessage()).toBe(
			"Selecciona un perfil de consumo para el simulador.",
		);
		httpMock.expectNone("/api/v1/devices/simulated");
	});

	it("envía claim para dispositivos físicos", () => {
		const fixture = TestBed.createComponent(DevicesComponent);
		const component = fixture.componentInstance;

		component.deviceForm.setValue({
			deviceKind: "physical",
			name: "Medidor nave",
			macAddress: "9070694d3590",
			simulationProfile: null,
		});

		component.onSubmit();

		const req = httpMock.expectOne("/api/v1/devices/claim");
		expect(req.request.method).toBe("POST");
		expect(req.request.body).toEqual({
			name: "Medidor nave",
			macAddress: "9070694d3590",
		});
		req.flush({
			id: 1,
			username: "admin@wattimizer.dev",
			name: "Medidor nave",
			macAddress: "9070694d3590",
			isOn: true,
			simulated: false,
			simulationProfile: null,
		});
	});

	it("envía alta simulada con perfil seleccionado", () => {
		const fixture = TestBed.createComponent(DevicesComponent);
		const component = fixture.componentInstance;

		component.deviceForm.setValue({
			deviceKind: "simulated",
			name: "Simulador nevera",
			macAddress: "",
			simulationProfile: "FRIDGE",
		});

		component.onSubmit();

		const req = httpMock.expectOne("/api/v1/devices/simulated");
		expect(req.request.method).toBe("POST");
		expect(req.request.body).toEqual({
			name: "Simulador nevera",
			simulationProfile: "FRIDGE",
		});
		req.flush({
			id: 2,
			username: "admin@wattimizer.dev",
			name: "Simulador nevera",
			macAddress: "SIM000000010",
			isOn: true,
			simulated: true,
			simulationProfile: "FRIDGE",
		});
	});
});
