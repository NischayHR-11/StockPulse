import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  ApiRequestError,
  createOrder,
  createProduct,
  generateSuggestions,
  getProducts,
  getSuggestions,
  resolveSuggestion,
} from './api/products';
import type { Product, Suggestion } from './types/product';

type Feedback = { kind: 'success' | 'error' | 'warning' | 'loading'; message: string };

function formatError(cause: unknown, fallback: string): string {
  if (!(cause instanceof ApiRequestError)) {
    return cause instanceof Error ? cause.message : fallback;
  }
  if (cause.status === 401) return 'Authentication is required for this action.';
  if (cause.status === 403) return 'You are not authorized to perform this action.';
  if (cause.status === 409) return `${cause.message || 'This request conflicts with current data.'} Try refreshing the product and retrying.`;
  if (cause.status === 400 && Object.keys(cause.fieldErrors).length > 0) {
    return Object.entries(cause.fieldErrors).map(([field, message]) => `${field}: ${message}`).join(' ');
  }
  return cause.message || fallback;
}

function formatCurrency(value: number | null): string {
  return value == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
}

const initialForm = {
  sku: '',
  name: '',
  category: 'APPAREL' as Product['category'],
  currentPrice: 10,
  stockLevel: 10,
  reorderThreshold: 5,
  demandVelocity: 1,
};

export default function App() {
  const [products, setProducts] = useState<Product[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [form, setForm] = useState(initialForm);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [catalogFeedback, setCatalogFeedback] = useState<Feedback | null>(null);
  const [suggestionFeedback, setSuggestionFeedback] = useState<Feedback | null>(null);
  const [productFeedback, setProductFeedback] = useState<Feedback | null>(null);
  const [saleFeedback, setSaleFeedback] = useState<Feedback | null>(null);
  const [quantityError, setQuantityError] = useState<string | null>(null);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [productBusy, setProductBusy] = useState(false);
  const [saleBusy, setSaleBusy] = useState<string | null>(null);
  const [suggestionBusy, setSuggestionBusy] = useState<string | null>(null);
  const [orderKeys, setOrderKeys] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<Feedback | null>(null);

  const notify = (feedback: Feedback) => {
    setToast(feedback);
    if (feedback.kind !== 'loading') {
      window.setTimeout(() => setToast(null), 4200);
    }
  };

  const loadProducts = () => {
    setLoading(true);
    getProducts()
      .then(setProducts)
      .catch((cause: unknown) => setCatalogFeedback({ kind: 'error', message: formatError(cause, 'Unable to load products. Refresh and try again.') }))
      .finally(() => setLoading(false));
  };

  useEffect(loadProducts, []);

  const loadSuggestions = (productId: string) => {
    setSelected(productId);
    setSuggestionsLoading(true);
    setSuggestionFeedback(null);
    getSuggestions(productId)
      .then(setSuggestions)
      .catch((cause: unknown) => setSuggestionFeedback({ kind: 'error', message: formatError(cause, 'Unable to load suggestions. Refresh and try again.') }))
      .finally(() => setSuggestionsLoading(false));
  };

  const orderKeyFor = (productId: string) => {
    const existing = orderKeys[productId];
    if (existing) return existing;
    const next = `console-${productId}-${crypto.randomUUID()}`;
    setOrderKeys((keys) => ({ ...keys, [productId]: next }));
    return next;
  };

  const submitProduct = async (event: FormEvent) => {
    event.preventDefault();
    setProductFeedback(null);
    setProductBusy(true);
    try {
      await createProduct(form);
      setProductFeedback({ kind: 'success', message: 'Product created successfully.' });
      notify({ kind: 'success', message: 'Product created.' });
      setForm(initialForm);
      loadProducts();
    } catch (cause) {
      setProductFeedback({ kind: 'error', message: formatError(cause, 'Product could not be created. Check the fields and try again.') });
    } finally {
      setProductBusy(false);
    }
  };

  const submitOrder = async (product: Product) => {
    if (!Number.isInteger(quantity) || quantity < 1) {
      setQuantityError('Enter a whole sale quantity of at least 1.');
      setSaleFeedback({ kind: 'warning', message: 'Enter a valid sale quantity before selling stock.' });
      return;
    }
    setQuantityError(null);
    setSaleFeedback({ kind: 'loading', message: `Processing a sale of ${quantity} unit${quantity === 1 ? '' : 's'}…` });
    setSaleBusy(product.id);
    try {
      await createOrder(product.id, quantity, orderKeyFor(product.id));
      setSaleFeedback({ kind: 'success', message: `Sale completed: ${quantity} unit${quantity === 1 ? '' : 's'} removed from ${product.name}.` });
      notify({ kind: 'success', message: 'Order processed.' });
      loadProducts();
      if (selected === product.id) loadSuggestions(product.id);
    } catch (cause) {
      setSaleFeedback({ kind: 'error', message: formatError(cause, 'The sale could not be completed. Check stock and try again.') });
    } finally {
      setSaleBusy(null);
    }
  };

  const submitSuggestions = async (event: FormEvent) => {
    event.preventDefault();
    if (!selectedProduct) return;
    if (!Number.isInteger(quantity) || quantity < 1) {
      setQuantityError('Enter a whole sale quantity of at least 1.');
      setSuggestionFeedback({ kind: 'warning', message: 'Enter a valid sale quantity before generating recommendations.' });
      return;
    }
    setQuantityError(null);
    setSuggestionFeedback({ kind: 'loading', message: `Generating recommendations for ${selectedProduct.name}…` });
    setSuggestionsLoading(true);
    try {
      const generated = await generateSuggestions(selectedProduct.id);
      setSuggestions((current) => [...generated, ...current.filter((item) => !generated.some((next) => next.id === item.id))]);
      setSuggestionFeedback({ kind: 'success', message: generated.length ? `${generated.length} recommendation${generated.length === 1 ? '' : 's'} ready for review.` : 'No new recommendations were created.' });
      notify({ kind: 'success', message: 'Suggestions generated.' });
    } catch (cause) {
      setSuggestionFeedback({ kind: 'error', message: formatError(cause, 'Recommendations could not be generated. Try again.') });
    } finally {
      setSuggestionsLoading(false);
    }
  };

  const handleSuggestionResolution = async (suggestion: Suggestion, resolution: 'APPROVE' | 'REJECT') => {
    setSuggestionBusy(suggestion.id);
    setSuggestionFeedback({ kind: 'loading', message: `${resolution === 'APPROVE' ? 'Accepting' : 'Rejecting'} recommendation…` });
    try {
      const updated = await resolveSuggestion(suggestion.id, resolution);
      setSuggestions((current) => current.map((item) => item.id === updated.id ? updated : item));
      setSuggestionFeedback({ kind: 'success', message: `Recommendation ${resolution === 'APPROVE' ? 'accepted' : 'rejected'} successfully.` });
      notify({ kind: 'success', message: `Suggestion ${resolution === 'APPROVE' ? 'accepted' : 'rejected'}.` });
      loadProducts();
    } catch (cause) {
      setSuggestionFeedback({ kind: 'error', message: formatError(cause, 'The recommendation could not be updated. Refresh and try again.') });
    } finally {
      setSuggestionBusy(null);
    }
  };

  const selectedProduct = products.find((product) => product.id === selected);
  const pending = suggestions.filter((suggestion) => suggestion.status === 'PENDING');

  return (
    <main className="page-shell">
      {toast && <div className={`toast toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>{toast.message}</div>}
      <header className="topbar"><div><p className="eyebrow">COMMERCE INTELLIGENCE</p><h1>StockPulse</h1></div><span className="connection-pill"><span className="status-dot" /> Inventory console</span></header>
      <section className="intro"><p className="eyebrow">OVERVIEW</p><h2>Inventory &amp; pricing</h2><p className="muted">Monitor stock, simulate sales, and review recommendations before applying them.</p></section>
      <section className="metric-grid" aria-label="Inventory summary">
        <article className="metric-card"><span className="metric-label">Products</span><strong>{loading ? '—' : products.length}</strong></article>
        <article className="metric-card"><span className="metric-label">Below reorder threshold</span><strong>{products.filter((product) => product.stockLevel < product.reorderThreshold).length}</strong></article>
        <article className="metric-card"><span className="metric-label">Pending suggestions</span><strong>{pending.length}</strong></article>
      </section>
      <section className="panel catalog-panel">
        <div className="panel-heading"><div><p className="section-kicker">INVENTORY</p><h3>Product catalog</h3><p className="muted">Choose a product to review, sell, or generate recommendations.</p></div><button type="button" className="secondary-button" onClick={loadProducts} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh catalog'}</button></div>
        {catalogFeedback && <FeedbackMessage feedback={catalogFeedback} />}
        {loading ? <p className="state-message">Loading products…</p> : products.length === 0 ? <p className="state-message">No products yet. Create one below to get started.</p> : <div className="table-wrap"><table><thead><tr><th>Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Units sold / 24h</th><th>Actions</th></tr></thead><tbody>{products.map((product) => <tr className={selected === product.id ? 'selected-row' : ''} key={product.id}><td><strong>{product.name}</strong><small>{product.sku}{selected === product.id && <span className="selected-label">Selected</span>}</small></td><td>{product.category}</td><td>{formatCurrency(product.currentPrice)}</td><td className={product.stockLevel < product.reorderThreshold ? 'stock-low' : ''}>{product.stockLevel}<small>Threshold: {product.reorderThreshold}</small></td><td>{product.unitsSoldLast24Hours}</td><td><div className="table-actions"><button type="button" disabled={suggestionsLoading || Boolean(saleBusy)} className={selected === product.id ? 'secondary-button selected-button' : 'secondary-button'} onClick={() => loadSuggestions(product.id)}>{selected === product.id ? 'Selected' : 'Review'}</button><button type="button" disabled={Boolean(saleBusy)} className="secondary-button" onClick={() => submitOrder(product)}>{saleBusy === product.id ? 'Selling…' : `Sell ${quantity}`}</button></div></td></tr>)}</tbody></table></div>}
        {saleFeedback && <div className="action-feedback sale-feedback"><FeedbackMessage feedback={saleFeedback} /><p className="feedback-hint">Sale quantity is controlled in the Suggestions workspace below.</p></div>}
      </section>
      <section className="workspace-grid">
        <form className="panel product-form" onSubmit={submitProduct}>
          <div className="panel-heading"><div><p className="section-kicker">CATALOG SETUP</p><h3>Create product</h3><p className="muted">Add inventory to the product catalog.</p></div></div>
          <div className="form-grid">
            <label>SKU<input required value={form.sku} onChange={(event) => setForm({ ...form, sku: event.target.value })} /></label>
            <label>Product name<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
            <label>Category<select value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value as Product['category'] })}><option value="APPAREL">Apparel</option><option value="ELECTRONICS">Electronics</option><option value="HOME">Home</option></select></label>
            <label>Current price<input required type="number" min="0.01" step="0.01" value={form.currentPrice} onChange={(event) => setForm({ ...form, currentPrice: Number(event.target.value) })} /></label>
            <label>Stock level<input required type="number" min="0" step="1" value={form.stockLevel} onChange={(event) => setForm({ ...form, stockLevel: Number(event.target.value) })} /></label>
            <label>Reorder threshold<input required type="number" min="0" step="1" value={form.reorderThreshold} onChange={(event) => setForm({ ...form, reorderThreshold: Number(event.target.value) })} /></label>
            <label>Demand velocity<input required type="number" min="0" step="1" value={form.demandVelocity} onChange={(event) => setForm({ ...form, demandVelocity: Number(event.target.value) })} /></label>
          </div>
          <button type="submit" disabled={productBusy} className="primary-button">{productBusy ? 'Creating product…' : 'Create product'}</button>
          {productFeedback && <FeedbackMessage feedback={productFeedback} />}
        </form>
        <section className="panel suggestions-panel" aria-labelledby="suggestions-heading">
          <div className="panel-heading"><div><p className="section-kicker">DECISION SUPPORT</p><h3 id="suggestions-heading">{selectedProduct ? `Suggestions · ${selectedProduct.name}` : 'Suggestion review'}</h3><p className="muted">{selectedProduct ? 'Generate and review recommendations for the selected product.' : 'Select Review on a product to begin.'}</p></div></div>
          {!selectedProduct ? <p className="state-message">No product selected. Choose Review in the catalog above.</p> : <form className="recommendation-form" onSubmit={submitSuggestions}>
            <div className="recommendation-heading"><div><span className="form-label">Recommendation workspace</span><p className="muted">Sale quantity used for the selected product.</p></div><span className="selection-chip">{selectedProduct.sku}</span></div>
            <div className="recommendation-controls"><label>Sale quantity<input aria-describedby={quantityError ? 'quantity-error' : 'quantity-help'} type="number" min="1" step="1" value={quantity} onInvalid={() => { setQuantityError('Enter a whole sale quantity of at least 1.'); setSuggestionFeedback({ kind: 'warning', message: 'Enter a valid sale quantity before generating recommendations.' }); }} onChange={(event) => { setQuantity(Number(event.target.value)); setQuantityError(null); setSuggestionFeedback(null); }} />{quantityError ? <span id="quantity-error" className="field-error">{quantityError}</span> : <span id="quantity-help" className="field-help">Enter a whole number of units.</span>}</label><button type="submit" disabled={suggestionsLoading} className="primary-button recommendation-button">{suggestionsLoading ? 'Generating…' : 'Generate suggestions'}</button></div>
            {suggestionFeedback && <FeedbackMessage feedback={suggestionFeedback} />}
          </form>}
          {selectedProduct && !suggestionsLoading && suggestions.length === 0 && <p className="state-message">No suggestions yet. Generate recommendations to see the review cards.</p>}
          <div className="suggestions-list">{suggestions.map((suggestion) => {
            const sourceLabel = suggestion.suggestionSource === 'GEMINI'
              ? '✨ AI Model Suggestion'
              : suggestion.suggestionSource === 'RULE_BASED'
                ? 'Rule-Based Suggestion'
                : 'Source unavailable';
            const sourceDescription = suggestion.suggestionSource === 'GEMINI'
              ? 'Generated by Gemini and validated by StockPulse policies.'
              : suggestion.suggestionSource === 'RULE_BASED'
                ? 'Generated by StockPulse deterministic rules.'
                : 'The generation source was not recorded for this suggestion.';
            return <article className="suggestion-card" key={suggestion.id}><div className="suggestion-card-header"><div><span className="suggestion-type">{suggestion.type === 'PRICE_ADJUSTMENT' ? 'Pricing recommendation' : 'Reorder recommendation'}</span><h4>{suggestion.type.replace('_', ' ')}</h4><span className={`source-badge source-${suggestion.suggestionSource?.toLowerCase() ?? 'unavailable'}`} title={sourceDescription} aria-label={`${sourceLabel}. ${sourceDescription}`}>{sourceLabel}</span></div><span className={`status-badge status-${suggestion.status.toLowerCase()}`}>{suggestion.status}</span></div><p className="suggestion-rationale">{suggestion.rationale}</p><div className="suggestion-meta"><span>Confidence <strong>{Math.round(suggestion.confidence * 100)}%</strong></span><span>Trigger <strong>{suggestion.trigger.replace('_', ' ')}</strong></span>{suggestion.proposedPrice != null && <span>Proposed price <strong>{formatCurrency(suggestion.proposedPrice)}</strong></span>}{suggestion.recommendedQuantity != null && <span>Recommended units <strong>{suggestion.recommendedQuantity}</strong></span>}</div>{suggestion.status === 'PENDING' && <div className="suggestion-actions"><button type="button" disabled={Boolean(suggestionBusy)} className="primary-button accept-button" onClick={() => handleSuggestionResolution(suggestion, 'APPROVE')}>{suggestionBusy === suggestion.id ? 'Updating…' : 'Accept recommendation'}</button><button type="button" disabled={Boolean(suggestionBusy)} className="secondary-button reject-button" onClick={() => handleSuggestionResolution(suggestion, 'REJECT')}>Reject</button></div>}</article>;
          })}</div>
        </section>
      </section>
      <footer>StockPulse · Built for the Zycus AI-Native Java Engineer hackathon</footer>
    </main>
  );
}

function FeedbackMessage({ feedback }: { feedback: Feedback }) {
  return <div className={`feedback feedback-${feedback.kind}`} role={feedback.kind === 'error' ? 'alert' : 'status'}><span className="feedback-icon" aria-hidden="true">{feedback.kind === 'success' ? '✓' : feedback.kind === 'error' ? '!' : feedback.kind === 'warning' ? '!' : '…'}</span><span>{feedback.message}</span></div>;
}
