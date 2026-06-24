export type SimulationProfile =
	| "SINE_WAVE"
	| "OVEN"
	| "WASHING_MACHINE"
	| "TELEVISION"
	| "FAN"
	| "DESKTOP_PC"
	| "FRIDGE"
	| "STANDBY"
	| "CONSTANT_HIGH_LOAD";

export type DeviceKind = "physical" | "simulated";

export interface SimulationProfileOption {
	value: SimulationProfile;
	label: string;
}

export const SIMULATION_PROFILE_OPTIONS: SimulationProfileOption[] = [
	{ value: "SINE_WAVE", label: "Onda de prueba" },
	{ value: "OVEN", label: "Horno" },
	{ value: "WASHING_MACHINE", label: "Lavadora" },
	{ value: "TELEVISION", label: "Televisor" },
	{ value: "FAN", label: "Ventilador" },
	{ value: "DESKTOP_PC", label: "PC" },
	{ value: "FRIDGE", label: "Nevera" },
	{ value: "STANDBY", label: "Consumo fantasma" },
	{ value: "CONSTANT_HIGH_LOAD", label: "Carga alta" },
];

export function simulationProfileLabel(
	profile: SimulationProfile | null | undefined,
): string {
	if (profile == null) {
		return "Sin perfil";
	}
	return (
		SIMULATION_PROFILE_OPTIONS.find((option) => option.value === profile)
			?.label ?? profile
	);
}
