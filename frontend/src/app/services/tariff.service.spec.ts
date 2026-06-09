import { provideHttpClient } from "@angular/common/http";
import {
	HttpTestingController,
	provideHttpClientTesting,
} from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { TariffService } from "./tariff.service";

describe("TariffService", () => {
	let service: TariffService;
	let httpMock: HttpTestingController;

	beforeEach(() => {
		TestBed.configureTestingModule({
			providers: [provideHttpClient(), provideHttpClientTesting()],
		});
		service = TestBed.inject(TariffService);
		httpMock = TestBed.inject(HttpTestingController);
	});

	afterEach(() => {
		httpMock.verify();
	});

	it("debe instanciarse correctamente", () => {
		expect(service).toBeTruthy();
	});

	it("getCatalog() hace GET a /api/v1/tariffs", () => {
		service.getCatalog().subscribe();
		const req = httpMock.expectOne("/api/v1/tariffs");
		expect(req.request.method).toBe("GET");
		req.flush([]);
	});

	it("getMyTariff() hace GET a /api/v1/users/me/tariff con observe:response", () => {
		let result: unknown;
		service.getMyTariff().subscribe((v) => {
			result = v;
		});

		const req = httpMock.expectOne("/api/v1/users/me/tariff");
		expect(req.request.method).toBe("GET");

		// Simulamos respuesta 200 con TariffDto
		req.flush(
			{
				id: 1,
				name: "Hogar peninsular",
				market: "libre",
				accessTariffCode: "2.0TD",
				geographicZone: "PENINSULA",
				energyCompany: "Endesa",
				periods: [],
				contractedPowers: [],
			},
			{ status: 200, statusText: "OK" },
		);

		expect(result).not.toBeNull();
	});

	it("getMyTariff() convierte respuesta 204 en null", () => {
		let result: unknown = "sin-inicializar";
		service.getMyTariff().subscribe((v) => {
			result = v;
		});

		const req = httpMock.expectOne("/api/v1/users/me/tariff");
		// Status 204 con cuerpo vacío: el backend indica que el usuario no tiene tarifa
		req.flush(null, { status: 204, statusText: "No Content" });

		expect(result).toBeNull();
	});

	it("saveMyTariff() hace POST a /api/v1/users/me/tariff", () => {
		service.saveMyTariff({ templateTariffId: 3, contract: null }).subscribe();

		const req = httpMock.expectOne("/api/v1/users/me/tariff");
		expect(req.request.method).toBe("POST");
		expect(req.request.body).toEqual({ templateTariffId: 3, contract: null });
		req.flush({
			id: 99,
			name: "Clon privado",
			market: "libre",
			accessTariffCode: "2.0TD",
			geographicZone: "PENINSULA",
			energyCompany: "Endesa",
			periods: [],
			contractedPowers: [],
		});
	});

	it("unlinkMyTariff() hace DELETE a /api/v1/users/me/tariff", () => {
		service.unlinkMyTariff().subscribe();
		const req = httpMock.expectOne("/api/v1/users/me/tariff");
		expect(req.request.method).toBe("DELETE");
		req.flush(null, { status: 204, statusText: "No Content" });
	});

	it("deleteCatalogTariff() hace DELETE a /api/v1/tariffs/{id}", () => {
		service.deleteCatalogTariff(5).subscribe();
		const req = httpMock.expectOne("/api/v1/tariffs/5");
		expect(req.request.method).toBe("DELETE");
		req.flush(null, { status: 204, statusText: "No Content" });
	});
});
