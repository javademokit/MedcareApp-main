import { useEffect, useState } from 'react';
import api from '../api';
import { money } from '../fmt';
import { Empty, ExpiryChip } from '../components/ui';

export default function Dashboard() {
  const [d, setD] = useState(null);
  const [exp, setExp] = useState([]);
  useEffect(() => {
    api.get('/dashboard').then((r) => setD(r.data));
    api.get('/inventory/expiring', { params: { days: 90 } }).then((r) => setExp(r.data.slice(0, 8)));
  }, []);
  if (!d) return <div className="empty">Loading dashboard...</div>;

  return (
    <>
      <div className="head"><h1>Today at the counter</h1></div>
      <div className="ledger">
        <div><span>Sales today</span><b>{money(d.todaySales)}</b></div>
        <div><span>Bills today</span><b>{d.todayBills}</b></div>
        <div><span>Batches expiring in 90 days</span><b style={{ color: d.expiringSoon ? 'var(--amber)' : undefined }}>{d.expiringSoon}</b></div>
        <div><span>Expired batches still in stock</span><b style={{ color: d.expired ? 'var(--red)' : undefined }}>{d.expired}</b></div>
      </div>
      <div className="grid2">
        <div>
          <h2>Expiring or expired</h2>
          <table className="tbl">
            <thead><tr><th>Medicine</th><th>Batch</th><th>Expiry</th><th className="num">Units</th></tr></thead>
            <tbody>
              {exp.map((b) => (
                <tr key={b.id}><td>{b.medicineName}</td><td>{b.batchNo}</td><td><ExpiryChip value={b.expiryDate} /></td><td className="num">{b.qtyAvailable}</td></tr>
              ))}
            </tbody>
          </table>
          {!exp.length && <Empty>No batches near expiry.</Empty>}
        </div>
        <div>
          <h2>Below reorder level</h2>
          <table className="tbl">
            <thead><tr><th>Medicine</th><th className="num">In stock</th><th className="num">Reorder at</th></tr></thead>
            <tbody>
              {d.lowStock.map((r) => (
                <tr key={r.medicineId}><td>{r.name}</td><td className="num" style={{ color: 'var(--red)', fontWeight: 600 }}>{r.stock}</td><td className="num">{r.reorderLevel}</td></tr>
              ))}
            </tbody>
          </table>
          {!d.lowStock.length && <Empty>Everything is above its reorder level.</Empty>}
        </div>
      </div>
    </>
  );
}
