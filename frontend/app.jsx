/* ================================================================
   ShopNova — Online Shopping Platform
   React Frontend Application
   
   Pages: Product Listing, Cart, Checkout
   Features: WCAG 2.1 AA, ARIA labels, loading/error states
   API: /api/v1/products (contract-first)
   ================================================================ */

const { useState, useEffect, useCallback, useReducer, createContext, useContext, useRef } = React;

// ================================================================
// API Configuration
// ================================================================
const API_BASE = '/api/v1';

// Mock data for standalone demo (replaces API calls when backend is unavailable)
const MOCK_PRODUCTS = [
  {
    id: '550e8400-e29b-41d4-a716-446655440001',
    name: 'Quantum Pro Wireless Headphones',
    description: 'Premium ANC headphones with 40-hour battery and spatial audio',
    price: 249.99, category: 'Electronics', rating: 4.8, stock: 42,
    sku: 'ELEC-000001',
    imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440002',
    name: 'Nebula Smart Watch Ultra',
    description: 'Advanced fitness tracking with AMOLED display and 14-day battery',
    price: 399.99, category: 'Electronics', rating: 4.6, stock: 28,
    sku: 'ELEC-000002',
    imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440003',
    name: 'Artisan Leather Messenger Bag',
    description: 'Hand-crafted Italian leather with brass hardware',
    price: 189.99, category: 'Fashion', rating: 4.9, stock: 15,
    sku: 'FASH-000001',
    imageUrl: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440004',
    name: 'Minimalist Ceramic Desk Lamp',
    description: 'Scandinavian-inspired LED lamp with wireless charging base',
    price: 79.99, category: 'Home', rating: 4.4, stock: 63,
    sku: 'HOME-000001',
    imageUrl: 'https://images.unsplash.com/photo-1507473885765-e6ed057ab6fe?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440005',
    name: 'Precision Chef Knife Set',
    description: 'Japanese stainless steel, 8-piece set with magnetic block',
    price: 329.99, category: 'Kitchen', rating: 4.7, stock: 19,
    sku: 'KITC-000001',
    imageUrl: 'https://images.unsplash.com/photo-1593618998160-e34014e67546?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440006',
    name: 'Aurora Running Shoes',
    description: 'Ultra-light mesh upper with responsive CloudFoam cushioning',
    price: 159.99, category: 'Sports', rating: 4.5, stock: 87,
    sku: 'SPRT-000001',
    imageUrl: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440007',
    name: 'Botanical Scented Candle Collection',
    description: 'Hand-poured soy wax candles — Lavender, Eucalyptus, Cedar',
    price: 49.99, category: 'Home', rating: 4.3, stock: 120,
    sku: 'HOME-000002',
    imageUrl: 'https://images.unsplash.com/photo-1602028915047-37269d1a73f7?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440008',
    name: 'Graphite Pro Mechanical Keyboard',
    description: 'Hot-swappable switches, RGB, CNC aluminum frame',
    price: 199.99, category: 'Electronics', rating: 4.9, stock: 34,
    sku: 'ELEC-000003',
    imageUrl: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440009',
    name: 'Silk Blend Oversized Scarf',
    description: 'Luxurious cashmere-silk blend in midnight navy',
    price: 129.99, category: 'Fashion', rating: 4.6, stock: 22,
    sku: 'FASH-000002',
    imageUrl: 'https://images.unsplash.com/photo-1601924994987-69e26d50dc64?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440010',
    name: 'Titanium Travel Tumbler',
    description: 'Double-wall vacuum insulation, keeps drinks hot for 12 hours',
    price: 44.99, category: 'Kitchen', rating: 4.2, stock: 200,
    sku: 'KITC-000002',
    imageUrl: 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440011',
    name: 'Premium Yoga Mat Pro',
    description: 'Extra-thick 6mm eco-friendly natural rubber with alignment lines',
    price: 89.99, category: 'Sports', rating: 4.7, stock: 55,
    sku: 'SPRT-000002',
    imageUrl: 'https://images.unsplash.com/photo-1601925260368-ae2f83cf8b7f?w=400&h=400&fit=crop'
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440012',
    name: 'Wireless Charging Pad Duo',
    description: 'Simultaneous charging for phone and earbuds, 15W fast charge',
    price: 59.99, category: 'Electronics', rating: 4.1, stock: 0,
    sku: 'ELEC-000004',
    imageUrl: 'https://images.unsplash.com/photo-1586953208448-b95a79798f07?w=400&h=400&fit=crop'
  }
];

// ================================================================
// Context: Cart
// ================================================================
const CartContext = createContext();

function cartReducer(state, action) {
  switch (action.type) {
    case 'ADD_ITEM': {
      const existing = state.items.find(i => i.productId === action.payload.productId);
      let newItems;
      if (existing) {
        newItems = state.items.map(i =>
          i.productId === action.payload.productId
            ? { ...i, quantity: i.quantity + action.payload.quantity, lineTotal: (i.quantity + action.payload.quantity) * i.price }
            : i
        );
      } else {
        newItems = [...state.items, {
          ...action.payload,
          lineTotal: action.payload.price * action.payload.quantity
        }];
      }
      return recalculate({ ...state, items: newItems });
    }
    case 'UPDATE_QUANTITY': {
      const newItems = state.items.map(i =>
        i.productId === action.payload.productId
          ? { ...i, quantity: action.payload.quantity, lineTotal: action.payload.quantity * i.price }
          : i
      );
      return recalculate({ ...state, items: newItems });
    }
    case 'REMOVE_ITEM': {
      const newItems = state.items.filter(i => i.productId !== action.payload.productId);
      return recalculate({ ...state, items: newItems });
    }
    case 'APPLY_COUPON': {
      const discount = calculateCouponDiscount(action.payload, state.subtotal);
      return recalculate({ ...state, couponCode: action.payload, discount });
    }
    case 'CLEAR_CART':
      return { items: [], subtotal: 0, discount: 0, total: 0, couponCode: null, itemCount: 0 };
    default:
      return state;
  }
}

function recalculate(state) {
  const subtotal = state.items.reduce((acc, item) => acc + item.lineTotal, 0);
  const itemCount = state.items.reduce((acc, item) => acc + item.quantity, 0);
  const total = Math.max(0, subtotal - (state.discount || 0));
  return { ...state, subtotal, itemCount, total };
}

function calculateCouponDiscount(code, subtotal) {
  switch (code.toUpperCase()) {
    case 'SAVE10': return subtotal * 0.10;
    case 'SAVE20': return subtotal * 0.20;
    case 'FLAT50': return Math.min(50, subtotal);
    default: return 0;
  }
}

function CartProvider({ children }) {
  const [cart, dispatch] = useReducer(cartReducer, {
    items: [], subtotal: 0, discount: 0, total: 0, couponCode: null, itemCount: 0
  });
  return (
    <CartContext.Provider value={{ cart, dispatch }}>
      {children}
    </CartContext.Provider>
  );
}

function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used within CartProvider');
  return context;
}

// ================================================================
// Context: Toast Notifications
// ================================================================
const ToastContext = createContext();

function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const toastIdRef = useRef(0);

  const addToast = useCallback((message, type = 'success') => {
    const id = ++toastIdRef.current;
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3500);
  }, []);

  const removeToast = useCallback((id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ addToast }}>
      {children}
      <div className="toast-container" role="status" aria-live="polite" aria-label="Notifications">
        {toasts.map(t => (
          <div key={t.id} className={`toast toast--${t.type}`} role="alert">
            <span className="toast__icon">{t.type === 'success' ? '✓' : '✕'}</span>
            <span className="toast__message">{t.message}</span>
            <button className="toast__close" onClick={() => removeToast(t.id)} aria-label="Dismiss notification">×</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function useToast() {
  return useContext(ToastContext);
}

// ================================================================
// Custom Hook: useFetchProducts
// ================================================================
function useFetchProducts() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    async function fetchProducts() {
      setLoading(true);
      setError(null);

      try {
        const response = await fetch(`${API_BASE}/products`, { signal: controller.signal });
        if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        const data = await response.json();
        if (!cancelled) {
          setProducts(data.content || data);
          setLoading(false);
        }
      } catch (err) {
        if (err.name === 'AbortError') return;
        // Fallback to mock data for standalone demo
        if (!cancelled) {
          console.info('API unavailable, using demo data');
          setProducts(MOCK_PRODUCTS);
          setLoading(false);
        }
      }
    }

    fetchProducts();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, []);

  return { products, loading, error };
}

// ================================================================
// Component: Navigation
// ================================================================
function Navigation({ currentPage, onNavigate }) {
  const { cart } = useCart();
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <nav className={`nav ${scrolled ? 'scrolled' : ''}`} role="navigation" aria-label="Main navigation">
      <div className="nav__logo" onClick={() => onNavigate('products')} 
           role="button" tabIndex={0} aria-label="ShopNova home"
           onKeyDown={(e) => e.key === 'Enter' && onNavigate('products')}>
        ShopNova
      </div>
      <ul className="nav__links">
        <li><button className={`nav__link ${currentPage === 'products' ? 'active' : ''}`}
                    onClick={() => onNavigate('products')} aria-current={currentPage === 'products' ? 'page' : undefined}>
          Products
        </button></li>
        <li><button className={`nav__link ${currentPage === 'cart' ? 'active' : ''}`}
                    onClick={() => onNavigate('cart')} aria-current={currentPage === 'cart' ? 'page' : undefined}>
          Cart
        </button></li>
      </ul>
      <button className="nav__cart-btn" onClick={() => onNavigate('cart')}
              aria-label={`Shopping cart with ${cart.itemCount} items`}
              id="nav-cart-button">
        🛒 Cart
        {cart.itemCount > 0 && (
          <span className="nav__cart-badge" aria-hidden="true">{cart.itemCount}</span>
        )}
      </button>
    </nav>
  );
}

// ================================================================
// Component: Product Card
// ================================================================
function ProductCard({ product, style }) {
  const { dispatch } = useCart();
  const { addToast } = useToast();
  const isOutOfStock = product.stock === 0;

  const handleAddToCart = (e) => {
    e.stopPropagation();
    if (isOutOfStock) return;
    dispatch({
      type: 'ADD_ITEM',
      payload: {
        productId: product.id,
        productName: product.name,
        imageUrl: product.imageUrl,
        price: product.price,
        quantity: 1,
        category: product.category
      }
    });
    addToast(`${product.name} added to cart`);
  };

  const renderStars = (rating) => {
    const full = Math.floor(rating);
    const half = rating % 1 >= 0.5;
    let stars = '★'.repeat(full);
    if (half) stars += '½';
    stars += '☆'.repeat(5 - full - (half ? 1 : 0));
    return stars;
  };

  const stockLabel = isOutOfStock
    ? { text: 'Out of Stock', cls: 'out' }
    : product.stock <= 10
      ? { text: `Only ${product.stock} left`, cls: 'low' }
      : { text: 'In Stock', cls: 'available' };

  return (
    <article className="product-card" style={style}
             role="article" aria-label={`${product.name}, $${product.price.toFixed(2)}`}
             id={`product-${product.id}`}>
      <div className="product-card__image-wrapper">
        <img className="product-card__image" src={product.imageUrl}
             alt={product.name} loading="lazy" />
        {product.rating >= 4.7 && (
          <span className="product-card__badge" aria-label="Top rated product">Top Rated</span>
        )}
        <button className="product-card__wishlist" aria-label={`Add ${product.name} to wishlist`}>♡</button>
      </div>
      <div className="product-card__body">
        <span className="product-card__category">{product.category}</span>
        <h3 className="product-card__name">{product.name}</h3>
        <div className="product-card__rating" aria-label={`Rating: ${product.rating} out of 5`}>
          <span className="product-card__stars" aria-hidden="true">{renderStars(product.rating)}</span>
          <span className="product-card__rating-text">{product.rating}</span>
        </div>
        <div className="product-card__footer">
          <div className="product-card__price">
            <span className="product-card__price-currency">$</span>
            {product.price.toFixed(2)}
          </div>
          <button className="product-card__add-btn"
                  onClick={handleAddToCart}
                  disabled={isOutOfStock}
                  aria-label={isOutOfStock ? `${product.name} is out of stock` : `Add ${product.name} to cart`}
                  id={`add-to-cart-${product.id}`}>
            {isOutOfStock ? '✕' : '+'}
          </button>
        </div>
        <div className={`product-card__stock product-card__stock--${stockLabel.cls}`}>
          {stockLabel.text}
        </div>
      </div>
    </article>
  );
}

// ================================================================
// Component: Skeleton Loader
// ================================================================
function SkeletonCard() {
  return (
    <div className="skeleton-card" aria-hidden="true">
      <div className="skeleton-card__image skeleton-pulse"></div>
      <div className="skeleton-card__body">
        <div className="skeleton-line skeleton-line--short"></div>
        <div className="skeleton-line skeleton-line--medium"></div>
        <div className="skeleton-line"></div>
        <div className="skeleton-line skeleton-line--price"></div>
      </div>
    </div>
  );
}

function ProductGridSkeleton() {
  return (
    <div className="skeleton-grid" aria-label="Loading products" role="status">
      <span className="sr-only">Loading products...</span>
      {Array.from({ length: 8 }).map((_, i) => <SkeletonCard key={i} />)}
    </div>
  );
}

// ================================================================
// Page: Product Listing
// ================================================================
function ProductListingPage() {
  const { products, loading, error } = useFetchProducts();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [sortBy, setSortBy] = useState('name');
  const [showHero, setShowHero] = useState(true);

  const categories = [...new Set(products.map(p => p.category))].sort();

  const filtered = products
    .filter(p => {
      const matchesSearch = !search || 
        p.name.toLowerCase().includes(search.toLowerCase()) ||
        p.description.toLowerCase().includes(search.toLowerCase());
      const matchesCategory = !category || p.category === category;
      return matchesSearch && matchesCategory;
    })
    .sort((a, b) => {
      switch (sortBy) {
        case 'price-asc': return a.price - b.price;
        case 'price-desc': return b.price - a.price;
        case 'rating': return b.rating - a.rating;
        default: return a.name.localeCompare(b.name);
      }
    });

  if (error) {
    return (
      <div className="page">
        <div className="error-state" role="alert">
          <span className="error-state__icon">⚠️</span>
          <h2 className="error-state__title">Unable to Load Products</h2>
          <p className="error-state__message">{error}</p>
          <button className="btn btn--primary" onClick={() => window.location.reload()}>
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      {showHero && (
        <section className="hero" aria-label="Welcome banner">
          <div className="hero__badge">
            <span className="hero__badge-dot"></span>
            New Collection Available
          </div>
          <h1 className="hero__title">
            Discover <span className="hero__title-accent">Premium</span><br />
            Products You'll Love
          </h1>
          <p className="hero__description">
            Curated collections from top brands. Free shipping on orders over $99.
            Quality guaranteed with every purchase.
          </p>
          <div className="hero__actions">
            <button className="btn btn--primary btn--lg" onClick={() => setShowHero(false)}>
              Shop Now →
            </button>
            <button className="btn btn--secondary btn--lg">
              View Collections
            </button>
          </div>
        </section>
      )}

      <div className="page__header">
        <h2 className="page__title page__title--gradient">All Products</h2>
        <p className="page__subtitle">{filtered.length} products available</p>
      </div>

      <div className="filter-bar" role="search" aria-label="Product filters">
        <input className="filter-bar__search" type="search" 
               placeholder="Search products..." value={search}
               onChange={(e) => setSearch(e.target.value)}
               aria-label="Search products" id="product-search" />
        <select className="filter-bar__select" value={category}
                onChange={(e) => setCategory(e.target.value)}
                aria-label="Filter by category" id="category-filter">
          <option value="">All Categories</option>
          {categories.map(c => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <select className="filter-bar__select" value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                aria-label="Sort products" id="sort-products">
          <option value="name">Sort by Name</option>
          <option value="price-asc">Price: Low to High</option>
          <option value="price-desc">Price: High to Low</option>
          <option value="rating">Top Rated</option>
        </select>
      </div>

      {loading ? (
        <ProductGridSkeleton />
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <span className="empty-state__icon">🔍</span>
          <h3 className="empty-state__title">No Products Found</h3>
          <p className="empty-state__description">
            Try adjusting your search or filters to find what you're looking for.
          </p>
          <button className="btn btn--secondary" onClick={() => { setSearch(''); setCategory(''); }}>
            Clear Filters
          </button>
        </div>
      ) : (
        <div className="product-grid" role="list" aria-label="Product catalog">
          {filtered.map((product, i) => (
            <ProductCard key={product.id} product={product}
                         style={{ animationDelay: `${i * 60}ms` }} />
          ))}
        </div>
      )}
    </div>
  );
}

// ================================================================
// Page: Cart
// ================================================================
function CartPage({ onNavigate }) {
  const { cart, dispatch } = useCart();
  const { addToast } = useToast();
  const [couponInput, setCouponInput] = useState('');

  const handleApplyCoupon = () => {
    const valid = ['SAVE10', 'SAVE20', 'FLAT50'];
    if (valid.includes(couponInput.toUpperCase())) {
      dispatch({ type: 'APPLY_COUPON', payload: couponInput });
      addToast(`Coupon "${couponInput.toUpperCase()}" applied!`);
    } else {
      addToast('Invalid coupon code', 'error');
    }
  };

  if (cart.items.length === 0) {
    return (
      <div className="page">
        <div className="page__header">
          <h1 className="page__title page__title--gradient">Shopping Cart</h1>
        </div>
        <div className="empty-state">
          <span className="empty-state__icon">🛒</span>
          <h2 className="empty-state__title">Your Cart is Empty</h2>
          <p className="empty-state__description">
            Looks like you haven't added any items yet. Explore our products and find something you love!
          </p>
          <button className="btn btn--primary" onClick={() => onNavigate('products')}>
            Browse Products
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title page__title--gradient">Shopping Cart</h1>
        <p className="page__subtitle">{cart.itemCount} item{cart.itemCount !== 1 ? 's' : ''} in your cart</p>
      </div>

      <div className="cart-layout">
        <div className="cart-items" role="list" aria-label="Cart items">
          {cart.items.map((item, i) => (
            <div key={item.productId} className="cart-item" role="listitem"
                 style={{ animationDelay: `${i * 100}ms` }}
                 aria-label={`${item.productName}, quantity ${item.quantity}, $${item.lineTotal.toFixed(2)}`}>
              <img className="cart-item__image" src={item.imageUrl}
                   alt={item.productName} loading="lazy" />
              <div className="cart-item__info">
                <span className="cart-item__category">{item.category}</span>
                <h3 className="cart-item__name">{item.productName}</h3>
                <span className="cart-item__price">${item.price.toFixed(2)}</span>
                <div className="cart-item__actions">
                  <div className="cart-item__qty" role="group" aria-label={`Quantity for ${item.productName}`}>
                    <button className="cart-item__qty-btn"
                            onClick={() => {
                              if (item.quantity > 1) {
                                dispatch({ type: 'UPDATE_QUANTITY', payload: { productId: item.productId, quantity: item.quantity - 1 } });
                              }
                            }}
                            disabled={item.quantity <= 1}
                            aria-label="Decrease quantity">−</button>
                    <input className="cart-item__qty-value" type="text" readOnly
                           value={item.quantity} aria-label="Current quantity" />
                    <button className="cart-item__qty-btn"
                            onClick={() => {
                              dispatch({ type: 'UPDATE_QUANTITY', payload: { productId: item.productId, quantity: item.quantity + 1 } });
                            }}
                            aria-label="Increase quantity">+</button>
                  </div>
                  <button className="cart-item__remove"
                          onClick={() => {
                            dispatch({ type: 'REMOVE_ITEM', payload: { productId: item.productId } });
                            addToast(`${item.productName} removed from cart`);
                          }}
                          aria-label={`Remove ${item.productName} from cart`}>
                    Remove
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>

        <aside className="cart-summary" aria-label="Order summary">
          <h2 className="cart-summary__title">Order Summary</h2>
          <div className="cart-summary__row">
            <span className="cart-summary__label">Subtotal ({cart.itemCount} items)</span>
            <span className="cart-summary__value">${cart.subtotal.toFixed(2)}</span>
          </div>
          {cart.discount > 0 && (
            <div className="cart-summary__row">
              <span className="cart-summary__label">Discount ({cart.couponCode})</span>
              <span className="cart-summary__value cart-summary__value--discount">
                −${cart.discount.toFixed(2)}
              </span>
            </div>
          )}
          <div className="cart-summary__row">
            <span className="cart-summary__label">Shipping</span>
            <span className="cart-summary__value" style={{color: 'var(--color-accent-success)'}}>
              {cart.subtotal >= 99 ? 'FREE' : '$9.99'}
            </span>
          </div>
          <hr className="cart-summary__divider" />
          <div className="cart-summary__total">
            <span className="cart-summary__total-label">Total</span>
            <span className="cart-summary__total-value">
              ${(cart.total + (cart.subtotal < 99 ? 9.99 : 0)).toFixed(2)}
            </span>
          </div>

          <div className="cart-summary__coupon">
            <input className="cart-summary__coupon-input" type="text"
                   placeholder="Coupon code" value={couponInput}
                   onChange={(e) => setCouponInput(e.target.value)}
                   aria-label="Enter coupon code" id="coupon-input" />
            <button className="btn btn--secondary btn--sm" onClick={handleApplyCoupon}
                    id="apply-coupon-btn">
              Apply
            </button>
          </div>

          <button className="btn btn--primary cart-summary__checkout-btn"
                  onClick={() => onNavigate('checkout')}
                  id="proceed-to-checkout">
            Proceed to Checkout →
          </button>
        </aside>
      </div>
    </div>
  );
}

// ================================================================
// Page: Checkout
// ================================================================
function CheckoutPage({ onNavigate }) {
  const { cart, dispatch } = useCart();
  const { addToast } = useToast();
  const [paymentMethod, setPaymentMethod] = useState('credit_card');
  const [processing, setProcessing] = useState(false);
  const [orderPlaced, setOrderPlaced] = useState(false);
  const [orderId, setOrderId] = useState('');
  const [formData, setFormData] = useState({
    fullName: '', email: '', phone: '',
    address: '', city: '', state: '', zip: ''
  });
  const [errors, setErrors] = useState({});

  const handleChange = (field) => (e) => {
    setFormData(prev => ({ ...prev, [field]: e.target.value }));
    // Clear error on change
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: null }));
    }
  };

  const validate = () => {
    const newErrors = {};
    if (!formData.fullName.trim()) newErrors.fullName = 'Full name is required';
    if (!formData.email.trim()) newErrors.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) newErrors.email = 'Invalid email';
    if (!formData.phone.trim()) newErrors.phone = 'Phone is required';
    if (!formData.address.trim()) newErrors.address = 'Address is required';
    if (!formData.city.trim()) newErrors.city = 'City is required';
    if (!formData.state.trim()) newErrors.state = 'State is required';
    if (!formData.zip.trim()) newErrors.zip = 'ZIP code is required';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) {
      addToast('Please fill in all required fields', 'error');
      return;
    }

    setProcessing(true);

    // Simulate order placement
    await new Promise(resolve => setTimeout(resolve, 2000));

    const newOrderId = `ORD-${Date.now().toString(36).toUpperCase()}`;
    setOrderId(newOrderId);
    setOrderPlaced(true);
    dispatch({ type: 'CLEAR_CART' });
    setProcessing(false);
    addToast('Order placed successfully!');
  };

  if (cart.items.length === 0 && !orderPlaced) {
    return (
      <div className="page">
        <div className="empty-state">
          <span className="empty-state__icon">🛒</span>
          <h1 className="empty-state__title">Cart is Empty</h1>
          <p className="empty-state__description">Add items to your cart before checkout.</p>
          <button className="btn btn--primary" onClick={() => onNavigate('products')}>Browse Products</button>
        </div>
      </div>
    );
  }

  if (orderPlaced) {
    return (
      <div className="page">
        <div className="order-confirmation" role="status">
          <div className="order-confirmation__icon">✓</div>
          <h1 className="order-confirmation__title">Order Confirmed!</h1>
          <p className="order-confirmation__order-id">
            Order ID: <span>{orderId}</span>
          </p>
          <p className="page__subtitle" style={{ marginBottom: '32px' }}>
            Thank you for your purchase. You'll receive a confirmation email shortly.
          </p>
          <button className="btn btn--primary btn--lg" onClick={() => onNavigate('products')}>
            Continue Shopping
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title page__title--gradient">Checkout</h1>
        <p className="page__subtitle">Complete your order</p>
      </div>

      <form className="checkout-layout" onSubmit={handleSubmit} noValidate>
        <div className="checkout-form">
          {/* Shipping Information */}
          <section className="form-section" aria-label="Shipping information">
            <h2 className="form-section__title">
              <span className="form-section__title-number">1</span>
              Shipping Information
            </h2>
            <div className="form-group">
              <label className="form-group__label" htmlFor="checkout-name">Full Name *</label>
              <input className={`form-group__input ${errors.fullName ? 'form-group__input--error' : ''}`}
                     type="text" id="checkout-name" value={formData.fullName}
                     onChange={handleChange('fullName')} placeholder="John Doe"
                     aria-required="true" aria-invalid={!!errors.fullName} />
              {errors.fullName && <span className="form-group__error" role="alert">{errors.fullName}</span>}
            </div>
            <div className="form-row">
              <div className="form-group">
                <label className="form-group__label" htmlFor="checkout-email">Email *</label>
                <input className={`form-group__input ${errors.email ? 'form-group__input--error' : ''}`}
                       type="email" id="checkout-email" value={formData.email}
                       onChange={handleChange('email')} placeholder="john@example.com"
                       aria-required="true" aria-invalid={!!errors.email} />
                {errors.email && <span className="form-group__error" role="alert">{errors.email}</span>}
              </div>
              <div className="form-group">
                <label className="form-group__label" htmlFor="checkout-phone">Phone *</label>
                <input className={`form-group__input ${errors.phone ? 'form-group__input--error' : ''}`}
                       type="tel" id="checkout-phone" value={formData.phone}
                       onChange={handleChange('phone')} placeholder="+1 (555) 123-4567"
                       aria-required="true" aria-invalid={!!errors.phone} />
                {errors.phone && <span className="form-group__error" role="alert">{errors.phone}</span>}
              </div>
            </div>
            <div className="form-group">
              <label className="form-group__label" htmlFor="checkout-address">Address *</label>
              <input className={`form-group__input ${errors.address ? 'form-group__input--error' : ''}`}
                     type="text" id="checkout-address" value={formData.address}
                     onChange={handleChange('address')} placeholder="123 Main Street, Apt 4B"
                     aria-required="true" aria-invalid={!!errors.address} />
              {errors.address && <span className="form-group__error" role="alert">{errors.address}</span>}
            </div>
            <div className="form-row">
              <div className="form-group">
                <label className="form-group__label" htmlFor="checkout-city">City *</label>
                <input className={`form-group__input ${errors.city ? 'form-group__input--error' : ''}`}
                       type="text" id="checkout-city" value={formData.city}
                       onChange={handleChange('city')} placeholder="New York"
                       aria-required="true" />
                {errors.city && <span className="form-group__error" role="alert">{errors.city}</span>}
              </div>
              <div className="form-group">
                <label className="form-group__label" htmlFor="checkout-state">State *</label>
                <input className={`form-group__input ${errors.state ? 'form-group__input--error' : ''}`}
                       type="text" id="checkout-state" value={formData.state}
                       onChange={handleChange('state')} placeholder="NY"
                       aria-required="true" />
                {errors.state && <span className="form-group__error" role="alert">{errors.state}</span>}
              </div>
            </div>
            <div className="form-group" style={{ maxWidth: '200px' }}>
              <label className="form-group__label" htmlFor="checkout-zip">ZIP Code *</label>
              <input className={`form-group__input ${errors.zip ? 'form-group__input--error' : ''}`}
                     type="text" id="checkout-zip" value={formData.zip}
                     onChange={handleChange('zip')} placeholder="10001"
                     aria-required="true" />
              {errors.zip && <span className="form-group__error" role="alert">{errors.zip}</span>}
            </div>
          </section>

          {/* Payment Method */}
          <section className="form-section" aria-label="Payment method">
            <h2 className="form-section__title">
              <span className="form-section__title-number">2</span>
              Payment Method
            </h2>
            <div className="payment-methods" role="radiogroup" aria-label="Select payment method">
              {[
                { id: 'credit_card', icon: '💳', name: 'Credit Card' },
                { id: 'debit_card', icon: '🏦', name: 'Debit Card' },
                { id: 'upi', icon: '📱', name: 'UPI' },
                { id: 'net_banking', icon: '🌐', name: 'Net Banking' }
              ].map(method => (
                <div key={method.id}
                     className={`payment-method ${paymentMethod === method.id ? 'selected' : ''}`}
                     role="radio" aria-checked={paymentMethod === method.id}
                     tabIndex={0} onClick={() => setPaymentMethod(method.id)}
                     onKeyDown={(e) => e.key === 'Enter' && setPaymentMethod(method.id)}
                     aria-label={method.name}>
                  <div className="payment-method__icon">{method.icon}</div>
                  <div className="payment-method__name">{method.name}</div>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Order Summary */}
        <aside className="cart-summary" aria-label="Order summary">
          <h2 className="cart-summary__title">Order Summary</h2>
          {cart.items.map(item => (
            <div key={item.productId} className="cart-summary__row">
              <span className="cart-summary__label" style={{ fontSize: '0.85rem' }}>
                {item.productName} × {item.quantity}
              </span>
              <span className="cart-summary__value">${item.lineTotal.toFixed(2)}</span>
            </div>
          ))}
          <hr className="cart-summary__divider" />
          <div className="cart-summary__row">
            <span className="cart-summary__label">Subtotal</span>
            <span className="cart-summary__value">${cart.subtotal.toFixed(2)}</span>
          </div>
          {cart.discount > 0 && (
            <div className="cart-summary__row">
              <span className="cart-summary__label">Discount</span>
              <span className="cart-summary__value cart-summary__value--discount">
                −${cart.discount.toFixed(2)}
              </span>
            </div>
          )}
          <div className="cart-summary__row">
            <span className="cart-summary__label">Shipping</span>
            <span className="cart-summary__value" style={{color: 'var(--color-accent-success)'}}>
              {cart.subtotal >= 99 ? 'FREE' : '$9.99'}
            </span>
          </div>
          <div className="cart-summary__row">
            <span className="cart-summary__label">Tax (8%)</span>
            <span className="cart-summary__value">${(cart.subtotal * 0.08).toFixed(2)}</span>
          </div>
          <hr className="cart-summary__divider" />
          <div className="cart-summary__total">
            <span className="cart-summary__total-label">Total</span>
            <span className="cart-summary__total-value">
              ${(cart.total + (cart.subtotal < 99 ? 9.99 : 0) + cart.subtotal * 0.08).toFixed(2)}
            </span>
          </div>
          <button className="btn btn--primary cart-summary__checkout-btn"
                  type="submit" disabled={processing} id="place-order-btn">
            {processing ? 'Processing...' : 'Place Order →'}
          </button>
        </aside>
      </form>
    </div>
  );
}

// ================================================================
// App: Router + Layout
// ================================================================
function App() {
  const [currentPage, setCurrentPage] = useState('products');

  const handleNavigate = useCallback((page) => {
    setCurrentPage(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  const renderPage = () => {
    switch (currentPage) {
      case 'products': return <ProductListingPage />;
      case 'cart': return <CartPage onNavigate={handleNavigate} />;
      case 'checkout': return <CheckoutPage onNavigate={handleNavigate} />;
      default: return <ProductListingPage />;
    }
  };

  return (
    <CartProvider>
      <ToastProvider>
        <Navigation currentPage={currentPage} onNavigate={handleNavigate} />
        <main id="main-content" role="main">
          {renderPage()}
        </main>
      </ToastProvider>
    </CartProvider>
  );
}

// ================================================================
// Mount
// ================================================================
const root = ReactDOM.createRoot(document.getElementById('app-root'));
root.render(<App />);
