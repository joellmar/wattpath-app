// Tipos regulatorios de peaje de acceso según Circular CNMC 3/2020
export type AccessTariffCode = "2.0TD" | "3.0TD" | "6.1TD" | "6.2TD";

export type GeographicZone =
	| "PENINSULA"
	| "CANARIAS"
	| "ISLAS_BALEARES"
	| "CEUTA"
	| "MELILLA";

// P1-P6 son los periodos tarifarios estándar del mercado español
export type PeriodCode = "P1" | "P2" | "P3" | "P4" | "P5" | "P6";

// Simétrico con PeriodDto del backend (id, periodCode, priceKwh)
export interface PeriodRequest {
	id: number | null;
	periodCode: PeriodCode;
	priceKwh: number;
}

// Simétrico con TariffContractedPowerDto del backend
export interface TariffContractedPowerRequest {
	id: number | null;
	periodCode: PeriodCode;
	contractedPowerKw: number;
}

// Simétrico con TariffDto del backend (campos exactos del record Java)
export interface TariffRequest {
	id: number | null;
	name: string;
	market: string;
	accessTariffCode: AccessTariffCode;
	geographicZone: GeographicZone;
	energyCompany: string;
	periods: PeriodRequest[];
	contractedPowers: TariffContractedPowerRequest[];
}

// GET y POST/PUT devuelven el mismo shape; alias semántico para lecturas
export type TariffResponse = TariffRequest;

// Simétrico con UserTariffRequest del backend:
//   - Solo templateTariffId: el servicio clona la plantilla
//   - templateTariffId + contract: clona y aplica overrides
//   - Solo contract: crea/actualiza el contrato privado directamente
export interface UserTariffRequest {
	templateTariffId: number | null;
	contract: TariffRequest | null;
}
