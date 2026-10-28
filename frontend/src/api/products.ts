import type { Product } from '../types/product';
import type { Suggestion } from '../types/product';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function toApiError(response: Response): Promise<ApiRequestError> {
  let body: ApiError = {};
  try {
    body = await response.json() as ApiError;
  } catch {
    // Keep the HTTP status when the server does not return JSON.
  }
  return new ApiRequestError(
    response.status,
    body.message ?? `Request failed (HTTP ${response.status}).`,
    body.fieldErrors ?? {},
  );
}

export async function getProducts(signal?: AbortSignal): Promise<Product[]> {
  const response = await fetch(`${API_BASE_URL}/products`, {
    headers: { Accept: 'application/json' },
    signal,
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response.json() as Promise<Product[]>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) throw await toApiError(response);
  return response.json() as Promise<T>;
}

export function createProduct(input: Omit<Product, 'id' | 'status' | 'createdAt' | 'updatedAt' | 'unitsSoldLast24Hours'>) {
  return request<Product>('/products', { method: 'POST', body: JSON.stringify(input) });
}

export function createOrder(productId: string, quantity: number, idempotencyKey: string) {
  return request<unknown>(`/orders/products/${productId}`, {
    method: 'POST',
    body: JSON.stringify({ quantity, idempotencyKey }),
  });
}

export function getSuggestions(productId: string) {
  return request<Suggestion[]>(`/products/${productId}/suggestions`);
}

export function generateSuggestions(productId: string) {
  return request<Suggestion[]>(`/products/${productId}/suggestions?trigger=MANUAL`, { method: 'POST' });
}

export function resolveSuggestion(id: string, resolution: 'APPROVE' | 'REJECT') {
  return request<Suggestion>(`/suggestions/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ resolution }),
  });
}
