import { useEffect, useMemo, useRef, useState } from 'react';
import api, { errMsg } from '../api';
import { useAuth } from '../auth';
import { RESTRICTED } from '../config';
import { money } from '../fmt';
import Invoice from '../components/Invoice';
import { ExpiryChip, ScheduleBadge } from '../components/ui';

export default function Pos() {
  const { hasRole } = useAuth();
  const [q, setQ] = useState('');
  const [hits, setHits] = useState([]);
  const [cart, setCart] = useState([]);
  const [cust, setCust] = useState({ name: '', phone: '' });
  const [doc, setDoc] = useState({ name: '', regNo: '' });
  const [pay, setPay] = useState('CASH');
  const [inter, setInter] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [invoice, setInvoice] = useState(null);
  const searchRef = useRef();

  useEffect(() => {
    if (!q.trim()) return setHits([]);
    const t = setTimeout(() => api.get('/sales/lookup', { params: { q } }).then((r) => setHits(r.data)).catch(() => {}), 200);
    return () => clearTimeout(t);
  }, [q]);

  const needsRx = cart.some((i) => RESTRICTED.includes(i.schedule));
  const canRx = hasRole('PHARMACIST');
  const lineAmt = (i) => (Number(i.mrp) / i.unitsPerPack) * i.qty * (1 - (Number(i.discountPercent) || 0) / 100);
  const est = useMemo(() => cart.reduce((s, i) => s + lineAmt(i), 0), [cart]);

  const add = (m) => {
    if (m.stock <= 0) return;
    setCart((c) => {
      const i = c.findIndex((x) => x.medicineId === m.medicineId);
      if (i >= 0) { const n = [...c]; n[i] = { ...n[i], qty: Math.min(n[i].qty + 1, m.stock) }; return n; }
      return [...c, { ...m, qty: 1, discountPercent: 0 }];
    });
    setQ('');
    searchRef.current?.focus();
  };
  const patch = (id, p) => setCart((c) => c.map((i) => (i.medicineId === id ? { ...i, ...p } : i)));
  const remove = (id) => setCart((c) => c.filter((i) => i.medicineId !== id));

  const submit = async () => {
    if (!cart.length || busy) return;
    setBusy(true); setErr('');
    try {
      const { data } = await api.post('/sales', {
        customerName: cust.name, customerPhone: cust.phone, doctorName: doc.name, doctorRegNo: doc.regNo,
        interState: inter, paymentMode: pay,
        items: cart.map((i) => ({ medicineId: i.medicineId, qty: Number(i.qty), discountPercent: Number(i.discountPercent) || 0 })),
      });
      setInvoice(data); setCart([]); setCust({ name: '', phone: '' }); setDoc({ name: '', regNo: '' });
    } catch (e) { setErr(errMsg(e)); } finally { setBusy(false); }
  };

  useEffect(() => {
    const k = (e) => {
      if (e.key === 'F2') { e.preventDefault(); searchRef.current?.focus(); }
      if (e.key === 'F8') { e.preventDefault(); submit(); }
    };
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  });

  return (
    <>
      <div className="head"><h1>New bill</h1><span style={{ color: 'var(--muted)' }}>F2 search | F8 complete bill</span></div>
      {err && <div className="alert err">{err}</div>}
      <div className="pos">
        <div>
          <input ref={searchRef} autoFocus placeholder="Search by brand, salt or barcode" value={q} onChange={(e) => setQ(e.target.value)} />
          {hits.length > 0 && (
            <div className="hits">
              {hits.map((m) => (
                <button key={m.medicineId} className="hit" disabled={m.stock <= 0} onClick={() => add(m)}>
                  <span><b>{m.brandName}</b> <ScheduleBadge value={m.schedule} /><br /><small style={{ color: 'var(--muted)' }}>{m.genericName}</small></span>
                  <span>{m.stock > 0 ? <>{m.stock} units | {money(m.mrp)}/pack</> : 'Out of stock'}</span>
                  <ExpiryChip value={m.nearestExpiry} />
                </button>
              ))}
            </div>
          )}
          <div className="scroll" style={{ marginTop: 14 }}>
            <table className="tbl">
              <thead><tr><th>Item</th><th className="num">MRP/pack</th><th>Units</th><th>Disc %</th><th className="num">Amount</th><th /></tr></thead>
              <tbody>
                {cart.map((i) => (
                  <tr key={i.medicineId}>
                    <td><b>{i.brandName}</b> <ScheduleBadge value={i.schedule} /><br /><small style={{ color: 'var(--muted)' }}>{i.unitsPerPack} units per pack, {i.stock} available</small></td>
                    <td className="num">{money(i.mrp)}</td>
                    <td><input className="qty" type="number" min="1" max={i.stock} value={i.qty} onChange={(e) => patch(i.medicineId, { qty: Math.max(1, Math.min(i.stock, Number(e.target.value) || 1)) })} /></td>
                    <td><input className="qty" type="number" min="0" value={i.discountPercent} onChange={(e) => patch(i.medicineId, { discountPercent: e.target.value })} /></td>
                    <td className="num">{money(lineAmt(i))}</td>
                    <td><button className="link" onClick={() => remove(i.medicineId)}>Remove</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!cart.length && <div className="empty">Search for a medicine to add it to this bill. Batches are picked earliest-expiry first.</div>}
          </div>
        </div>

        <div className="panel stack">
          <h2>Customer</h2>
          <input placeholder={needsRx ? 'Patient name (required)' : 'Customer name'} value={cust.name} onChange={(e) => setCust({ ...cust, name: e.target.value })} />
          <input placeholder="Phone" value={cust.phone} onChange={(e) => setCust({ ...cust, phone: e.target.value })} />
          {needsRx && (
            <>
              {!canRx && <div className="alert warn">This bill has prescription-only medicines. Only a registered pharmacist can complete it.</div>}
              <input placeholder="Doctor name (required)" value={doc.name} onChange={(e) => setDoc({ ...doc, name: e.target.value })} />
              <input placeholder="Doctor registration no." value={doc.regNo} onChange={(e) => setDoc({ ...doc, regNo: e.target.value })} />
            </>
          )}
          <select value={pay} onChange={(e) => setPay(e.target.value)}>
            <option value="CASH">Cash</option><option value="UPI">UPI</option><option value="CARD">Card</option><option value="CREDIT">Customer credit</option>
          </select>
          <label className="row"><input type="checkbox" style={{ width: 'auto' }} checked={inter} onChange={(e) => setInter(e.target.checked)} /> Inter-state sale (IGST)</label>
          <div className="total big"><span>Estimated total</span><span>{money(est)}</span></div>
          <small style={{ color: 'var(--muted)' }}>The final amount, GST split and round-off are calculated on the server.</small>
          <button className="btn" disabled={!cart.length || busy || (needsRx && !canRx)} onClick={submit}>{busy ? 'Saving...' : 'Complete bill'}</button>
        </div>
      </div>
      {invoice && <Invoice sale={invoice} onClose={() => setInvoice(null)} />}
    </>
  );
}
