import type { Product } from '../types/product';
import type { Suggestion } from '../types/product';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export async function getProducts(signal?: AbortSignal): Promise<Product[]> {
  const response = await fetch(`${API_BASE_URL}/products`, {
    headers: { Accept: 'application/json' },
    signal,
  });
  if (!response.ok) {
    throw new Error(`Unable to load products (HTTP ${response.status}).`);
  }
  return response.json() as Promise<Product[]>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) throw new Error(`Request failed (HTTP ${response.status}).`);
  return response.json() as Promise<T>;
}

export function createProduct(input: Omit<Product, 'id' | 'status' | 'createdAt' | 'updatedAt'>) {
  return request<Product>('/products', { method: 'POST', body: JSON.stringify(input) });
}

export function createOrder(productId: string, quantity: number) {
  return request<unknown>(`/orders/products/${productId}`, {
    method: 'POST',
    body: JSON.stringify({ quantity, idempotencyKey: `console-${productId}-${Date.now()}` }),
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
