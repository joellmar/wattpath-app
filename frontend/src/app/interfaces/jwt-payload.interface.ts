export interface JwtPayload {
	exp?: number;
	username?: string;
	authorities?: string;
}
