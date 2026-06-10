export interface RegisterRequest {
	username: string;
	password: string;
	confirmPassword: string;
	tariffId?: number;
}
