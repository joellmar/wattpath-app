import type { SimulationProfile } from "./simulation-profile.interface";

export interface Device {
	id: number;
	username: string;
	name: string;
	macAddress: string;
	isOn: boolean;
	simulated: boolean;
	simulationProfile: SimulationProfile | null;
}

export interface ClaimDeviceRequest {
	name: string;
	macAddress: string;
}

export interface CreateSimulatedDeviceRequest {
	name: string;
	simulationProfile: SimulationProfile;
}
