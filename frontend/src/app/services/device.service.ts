import { httpResource } from "@angular/common/http";
import { Injectable } from "@angular/core";
import type { Device } from "../interfaces/device.interface";

@Injectable({
	providedIn: "root",
})
export class DeviceService {
	readonly devicesResource = httpResource<Device[]>(() => "/api/v1/devices", {
		defaultValue: [],
	});
}
