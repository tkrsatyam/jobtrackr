export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    userId: string;
    email: string;
    fullName: string;
    role: string;
}

export interface UserProfile {
    id: string;
    email: string;
    fullName: string;
    avatarUrl: string | null;
    role: string;
    provider: 'LOCAL' | 'GOOGLE';
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    email: string;
    password: string;
    fullName: string;
}

export interface RefreshRequest {
    refreshToken: string;
}