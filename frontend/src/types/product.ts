export type ProductCategory = 'ELECTRONICS' | 'APPAREL' | 'HOME';
export type ProductStatus = 'ACTIVE' | 'PRICE_REVIEW_PENDING' | 'OUT_OF_STOCK';

export interface Product {
  id: string;
  sku: string;
  name: string;
  category: ProductCategory;
  currentPrice: number;
  stockLevel: number;
  reorderThreshold: number;
  demandVelocity: number;
  unitsSoldLast24Hours: number;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export type SuggestionType = 'PRICE_ADJUSTMENT' | 'REORDER';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export type SuggestionDirection = 'INCREASE' | 'HOLD' | null;
export type SuggestionTrigger = 'INITIAL' | 'LOW_INVENTORY' | 'DEMAND_SPIKE' | 'MANUAL';
export type SuggestionSource = 'GEMINI' | 'RULE_BASED';

export interface Suggestion {
  id: string;
  productId: string;
  type: SuggestionType;
  status: SuggestionStatus;
  currentPrice: number | null;
  proposedPrice: number | null;
  recommendedQuantity: number | null;
  direction: SuggestionDirection;
  confidence: number;
  rationale: string;
  trigger: SuggestionTrigger;
  suggestionSource: SuggestionSource | null;
  createdAt: string;
  resolvedAt: string | null;
}
