import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import { LayoutDashboard, BarChart3, Network, Plus, Search } from 'lucide-react'
import './App.css'

interface UrlItem {
  id: number;
  shortCode: string;
  originalUrl: string;
  createdAt: string;
  expiresAt: string | null;
  clicks: number;
  rateLimitAlgorithm?: string;
  rateLimitCapacity?: number;
  rateLimitWindowSeconds?: number;
}

const API_BASE = 'http://localhost:8080/api/v1/urls';

function Dashboard() {
  const [urls, setUrls] = useState<UrlItem[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newUrl, setNewUrl] = useState('');
  const [expiryDays, setExpiryDays] = useState('');
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [cursor, setCursor] = useState<number | null>(null);

  // Rate Limit State
  const [rateLimitAlgorithm, setRateLimitAlgorithm] = useState('TOKEN_BUCKET');
  const [rateLimitCapacity, setRateLimitCapacity] = useState('');
  const [rateLimitWindow, setRateLimitWindow] = useState('');

  const fetchUrls = async (currentCursor: number | null = null) => {
    try {
      const url = new URL(API_BASE);
      if (currentCursor) url.searchParams.append('cursor', currentCursor.toString());
      url.searchParams.append('limit', '10');

      const res = await fetch(url.toString());
      if (res.ok) {
        const data: UrlItem[] = await res.json();
        if (data.length < 10) setHasMore(false);
        
        if (currentCursor) {
          setUrls(prev => [...prev, ...data]);
        } else {
          setUrls(data);
        }

        if (data.length > 0) {
          setCursor(data[data.length - 1].id);
        }
      }
    } catch (e) {
      console.error("Failed to fetch URLs", e);
    }
  };

  useEffect(() => {
    fetchUrls();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const body: any = { originalUrl: newUrl };
      if (expiryDays) body.expiryDays = parseInt(expiryDays);
      if (rateLimitAlgorithm) {
        body.rateLimitAlgorithm = rateLimitAlgorithm;
        body.rateLimitCapacity = parseInt(rateLimitCapacity) || 100;
        body.rateLimitWindowSeconds = parseInt(rateLimitWindow) || 60;
      }

      const res = await fetch(API_BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });

      if (res.ok) {
        setIsModalOpen(false);
        setNewUrl('');
        setExpiryDays('');
        setRateLimitAlgorithm('');
        setRateLimitCapacity('');
        setRateLimitWindow('');
        setCursor(null);
        setHasMore(true);
        fetchUrls(null);
      }
    } catch (e) {
      console.error("Failed to create", e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-content">
      <div className="page-header">
        <div>
          <h2>Control Panel</h2>
          <p className="text-muted">Manage your generated short URLs</p>
        </div>
        <button className="btn" onClick={() => setIsModalOpen(true)}>
          <Plus size={18} style={{ marginRight: '8px' }} /> Create URL
        </button>
      </div>

      <div className="glass-panel dashboard-card">
        <table className="url-table">
          <thead>
            <tr>
              <th>Short Code</th>
              <th>Original URL</th>
              <th>Created At</th>
              <th>Expires</th>
              <th>Clicks</th>
              <th>Rate Limit</th>
            </tr>
          </thead>
          <tbody>
            {urls.map(url => (
              <tr key={url.id}>
                <td>
                  <a href={`http://localhost:8080/api/v1/urls/${url.shortCode}`} target="_blank" rel="noreferrer" className="short-code">
                    /{url.shortCode}
                  </a>
                </td>
                <td><span className="original-url" title={url.originalUrl}>{url.originalUrl}</span></td>
                <td>{new Date(url.createdAt).toLocaleDateString()}</td>
                <td>{url.expiresAt ? new Date(url.expiresAt).toLocaleDateString() : 'Never'}</td>
                <td>{url.clicks}</td>
                <td>
                  {url.rateLimitAlgorithm 
                    ? <span className="badge">{url.rateLimitAlgorithm} ({url.rateLimitCapacity} req / {url.rateLimitWindowSeconds}s)</span>
                    : <span className="text-muted">None</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        
        {urls.length === 0 && (
          <div className="empty-state">
            <Search size={48} className="text-muted" style={{ marginBottom: '1rem', opacity: 0.5 }} />
            <p>No URLs found. Create your first one!</p>
          </div>
        )}

        {hasMore && urls.length > 0 && (
          <div className="load-more-container">
            <button className="btn btn-secondary" onClick={() => fetchUrls(cursor)}>Load More</button>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="modal-overlay" onClick={() => setIsModalOpen(false)}>
          <div className="glass-panel modal-content" onClick={e => e.stopPropagation()}>
            <h2 className="modal-title">Create New Short URL</h2>
            <form onSubmit={handleCreate}>
              <div className="form-group">
                <label>Original URL</label>
                <input 
                  type="url" 
                  className="form-control" 
                  value={newUrl} 
                  onChange={e => setNewUrl(e.target.value)} 
                  required 
                  placeholder="https://example.com/very/long/url"
                />
              </div>
              <div className="form-group">
                <label>Expiry (Days) - Optional</label>
                <input 
                  type="number" 
                  className="form-control" 
                  value={expiryDays} 
                  onChange={e => setExpiryDays(e.target.value)} 
                  placeholder="e.g. 30"
                  min="1"
                />
              </div>
              
              <hr style={{ margin: '1.5rem 0', borderColor: 'rgba(255,255,255,0.1)' }} />
              
              <div className="form-group">
                <label>Rate Limit Algorithm</label>
                <select 
                  className="form-control" 
                  value={rateLimitAlgorithm} 
                  onChange={e => setRateLimitAlgorithm(e.target.value)}
                  style={{ backgroundColor: 'rgba(0, 0, 0, 0.2)', color: 'white' }}
                >
                  <option value="TOKEN_BUCKET">Token Bucket (Active)</option>
                  <option value="FIXED_WINDOW" disabled>Fixed Window (Coming Soon)</option>
                  <option value="SLIDING_WINDOW_LOG" disabled>Sliding Window Log (Coming Soon)</option>
                  <option value="SLIDING_WINDOW_COUNTER" disabled>Sliding Window Counter (Coming Soon)</option>
                </select>
              </div>

              {rateLimitAlgorithm && (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div className="form-group">
                    <label>Capacity</label>
                    <input 
                      type="number" 
                      className="form-control" 
                      value={rateLimitCapacity} 
                      onChange={e => setRateLimitCapacity(e.target.value)} 
                      placeholder="e.g. 100"
                      min="1"
                      required={!!rateLimitAlgorithm}
                    />
                  </div>
                  <div className="form-group">
                    <label>Window (Seconds)</label>
                    <input 
                      type="number" 
                      className="form-control" 
                      value={rateLimitWindow} 
                      onChange={e => setRateLimitWindow(e.target.value)} 
                      placeholder="e.g. 60"
                      min="1"
                      required={!!rateLimitAlgorithm}
                    />
                  </div>
                </div>
              )}

              <div className="modal-actions" style={{ marginTop: '2rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>Cancel</button>
                <button type="submit" className="btn" disabled={loading}>
                  {loading ? 'Creating...' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

function Analytics() {
  return (
    <div className="page-content">
      <div className="page-header">
        <div>
          <h2>Analytics</h2>
          <p className="text-muted">Real-time clickstream data</p>
        </div>
      </div>
      <div className="glass-panel empty-state" style={{ padding: '4rem 2rem' }}>
        <BarChart3 size={64} style={{ color: 'var(--accent-primary)', marginBottom: '1.5rem', opacity: 0.8 }} />
        <h3 style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>Under Development</h3>
        <p className="text-muted" style={{ maxWidth: '400px', margin: '0 auto' }}>
          We are currently integrating ClickHouse and Kafka to bring you blazing-fast, real-time analytics. Check back soon!
        </p>
      </div>
    </div>
  )
}

function Architecture() {
  return (
    <div className="page-content">
      <div className="page-header">
        <div>
          <h2>System Architecture</h2>
          <p className="text-muted">How ThrottleX handles massive scale</p>
        </div>
      </div>
      <div className="glass-panel" style={{ padding: '2rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          
          <div className="arch-card">
            <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--accent-primary)', marginBottom: '1rem' }}>
              <Network size={20} /> Fail-Open Caching & Outbox Pattern
            </h3>
            <p className="text-muted" style={{ lineHeight: '1.6' }}>
              When Redis goes down, the Resilience4j Circuit Breaker transitions to OPEN. 
              New URL creations are spooled directly into a PostgreSQL Outbox table instead of failing. 
              The application degrades gracefully, ensuring 100% availability.
            </p>
          </div>

          <div className="arch-card">
            <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--accent-primary)', marginBottom: '1rem' }}>
              <Network size={20} /> Distributed State Restoration
            </h3>
            <p className="text-muted" style={{ lineHeight: '1.6' }}>
              When Redis recovers, the system drops a global flag (`throttlex:bloom:warmup_active`) 
              directly into Redis. All instances instantly enter "Warmup Mode" (bypassing the Bloom Filter).
              A Kafka worker then smoothly streams the PostgreSQL Outbox into Redis, deleting the flag when finished!
            </p>
          </div>
          
        </div>
      </div>
    </div>
  )
}

function Sidebar() {
  const location = useLocation();
  
  const navItems = [
    { path: '/', label: 'Control Panel', icon: <LayoutDashboard size={20} /> },
    { path: '/analytics', label: 'Analytics', icon: <BarChart3 size={20} /> },
    { path: '/architecture', label: 'Architecture', icon: <Network size={20} /> },
  ];

  return (
    <aside className="sidebar glass-panel">
      <div className="sidebar-header">
        <h1 className="title" style={{ fontSize: '1.75rem', margin: 0 }}>ThrottleX</h1>
      </div>
      <nav className="sidebar-nav">
        {navItems.map(item => {
          const isActive = location.pathname === item.path;
          return (
            <Link 
              key={item.path} 
              to={item.path} 
              className={`nav-item ${isActive ? 'active' : ''}`}
            >
              {item.icon}
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}

function App() {
  return (
    <BrowserRouter>
      <div className="app-layout">
        <Sidebar />
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/architecture" element={<Architecture />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}

export default App
