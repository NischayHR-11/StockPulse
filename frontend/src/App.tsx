import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  createOrder,
  createProduct,
  generateSuggestions,
  getProducts,
  getSuggestions,
  resolveSuggestion,
} from './api/products';
import type { Product, Suggestion } from './types/product';

function formatCurrency(value: number | null): string {
  return value == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
}

const initialForm = { sku: '', name: '', category: 'APPAREL' as Product['category'], currentPrice: 10, stockLevel: 10, reorderThreshold: 5, demandVelocity: 1 };

export default function App() {
  const [products, setProducts] = useState<Product[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [form, setForm] = useState(initialForm);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadProducts = () => {
    setLoading(true);
    getProducts().then(setProducts).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Unable to load products.')).finally(() => setLoading(false));
  };
  useEffect(loadProducts, []);

  const loadSuggestions = (productId: string) => {
    setSelected(productId);
    getSuggestions(productId).then(setSuggestions).catch(() => setError('Unable to load suggestions.'));
  };
  const run = (action: Promise<unknown>, message: string) => {
    setError(null);
    action.then(() => { setNotice(message); loadProducts(); if (selected) loadSuggestions(selected); }).catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Request failed.'));
  };
  const submitProduct = (event: FormEvent) => {
    event.preventDefault();
    run(createProduct(form), 'Product created.');
    setForm(initialForm);
  };
  const selectedProduct = products.find((product) => product.id === selected);
  const pending = suggestions.filter((suggestion) => suggestion.status === 'PENDING');

  return (
    <main className="page-shell">
      <header className="topbar"><div><p className="eyebrow">COMMERCE INTELLIGENCE</p><h1>StockPulse</h1></div><span className="connection-pill"><span className="status-dot" /> Inventory console</span></header>
      <section className="intro"><p className="eyebrow">OVERVIEW</p><h2>Inventory & pricing</h2><p className="muted">Monitor stock, simulate sales, and review recommendations before applying them.</p></section>
      {error && <div className="error-message" role="alert">{error}</div>}
      {notice && <div className="success-message" role="status">{notice}</div>}
      <section className="metric-grid" aria-label="Inventory summary">
        <article className="metric-card"><span className="metric-label">Products</span><strong>{loading ? '—' : products.length}</strong></article>
        <article className="metric-card"><span className="metric-label">Below reorder threshold</span><strong>{products.filter((product) => product.stockLevel < product.reorderThreshold).length}</strong></article>
        <article className="metric-card"><span className="metric-label">Pending suggestions</span><strong>{pending.length}</strong></article>
      </section>
      <section className="panel">
        <div className="panel-heading"><div><h3>Product catalog</h3><p className="muted">Select a product to operate on it.</p></div><button className="secondary-button" onClick={loadProducts}>Refresh</button></div>
        {loading ? <p className="state-message">Loading products…</p> : products.length === 0 ? <p className="state-message">No products yet.</p> : <div className="table-wrap"><table><thead><tr><th>Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Demand / 24h</th><th>Actions</th></tr></thead><tbody>{products.map((product) => <tr key={product.id}><td><strong>{product.name}</strong><small>{product.sku}</small></td><td>{product.category}</td><td>{formatCurrency(product.currentPrice)}</td><td className={product.stockLevel < product.reorderThreshold ? 'stock-low' : ''}>{product.stockLevel}<small>Threshold: {product.reorderThreshold}</small></td><td>{product.demandVelocity}</td><td><button className="secondary-button" onClick={() => loadSuggestions(product.id)}>Review</button><button className="secondary-button" onClick={() => run(createOrder(product.id, quantity), 'Order processed.')}>Sell {quantity}</button></td></tr>)}</tbody></table></div>}
      </section>
      <section className="panel workflow-grid">
        <form onSubmit={submitProduct}><h3>Create product</h3><input required placeholder="SKU" value={form.sku} onChange={(event) => setForm({ ...form, sku: event.target.value })} /><input required placeholder="Name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /><input required type="number" min="0.01" step="0.01" value={form.currentPrice} onChange={(event) => setForm({ ...form, currentPrice: Number(event.target.value) })} /><button className="primary-button">Create product</button></form>
        <div><h3>{selectedProduct ? `Suggestions · ${selectedProduct.name}` : 'Suggestion review'}</h3>{selectedProduct && <><label>Sale quantity <input type="number" min="1" value={quantity} onChange={(event) => setQuantity(Number(event.target.value))} /></label><button className="primary-button" onClick={() => run(generateSuggestions(selectedProduct.id), 'Suggestions generated.')}>Generate suggestions</button></>}{selected && suggestions.length === 0 && <p className="state-message">No suggestions.</p>}{suggestions.map((suggestion) => <article className="suggestion-card" key={suggestion.id}><strong>{suggestion.type.replace('_', ' ')}</strong><span className="status-badge">{suggestion.status}</span><p>{suggestion.rationale}</p><small>Confidence {Math.round(suggestion.confidence * 100)}% · {suggestion.trigger}</small>{suggestion.status === 'PENDING' && <div><button className="primary-button" onClick={() => run(resolveSuggestion(suggestion.id, 'APPROVE'), 'Suggestion accepted.')}>Accept</button><button className="secondary-button" onClick={() => run(resolveSuggestion(suggestion.id, 'REJECT'), 'Suggestion rejected.')}>Reject</button></div>}</article>)}</div>
      </section>
      <footer>StockPulse · Built for the Zycus AI-Native Java Engineer hackathon</footer>
    </main>
  );
}
