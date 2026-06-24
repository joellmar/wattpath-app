import type { Device } from "./device.interface";

export interface TelemetryState {
	devices: Device[];
	selectedMac: string | null;
	historicalReadings: {
		[mac: string]: {
			timestamps: string[];
			powerW: number[];
		};
	};
	isLoadingDevices: boolean;
}
