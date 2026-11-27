import { STORE } from '../config';
import { dateTime, date, money } from '../fmt';
import { Modal } from './ui';

export default function Invoice({ sale, onClose }) {
  const words = (s) => s; // keep simple; add number-to-words if needed for your invoice format
  return (
    <Modal title={`Invoice ${sale.invoiceNo}`} onClose={onClose} wide>
      <div className="invoice invoice-print">
        <h3>{STORE.name}</h3>
        <div>{STORE.address} | {STORE.phone}</div>
        <div>GSTIN: {STORE.gstin} | Drug licence: {STORE.drugLicence}</div>
        <div className="meta">
          <div>
            <b>Tax invoice</b> {sale.invoiceNo}
            {sale.status === 'CANCELLED' && <b> (CANCELLED)</b>}
            <br />
            {dateTime(sale.invoiceDate)}
          </div>
          <div>
            Patient: {sale.customerName || 'Walk-in'} {sale.customerPhone}
            {sale.doctorName && (<><br />Doctor: {sale.doctorName} {sale.doctorRegNo && `(Reg. ${sale.doctorRegNo})`}</>)}
          </div>
        </div>
        <table>
          <thead>
            <tr><th>Item</th><th>Batch</th><th>Exp</th><th>HSN</th><th className="r">MRP/pack</th><th className="r">Qty</th><th className="r">Disc%</th><th className="r">GST%</th><th className="r">Amount</th></tr>
          </thead>
          <tbody>
            {sale.items.map((l, i) => (
              <tr key={i}>
                <td>{l.name}</td><td>{l.batchNo}</td><td>{date(l.expiryDate)}</td><td>{l.hsn}</td>
                <td className="r">{Number(l.mrp).toFixed(2)}</td><td className="r">{l.qty}</td>
                <td className="r">{Number(l.discountPercent)}</td><td className="r">{Number(l.gstPercent)}</td>
                <td className="r">{Number(l.total).toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="r">
          Taxable {money(sale.taxable)}
          {sale.interState ? <> | IGST {money(sale.igst)}</> : <> | CGST {money(sale.cgst)} | SGST {money(sale.sgst)}</>}
          {' '}| Round off {money(sale.roundOff)}
          <br />
          <b style={{ fontSize: 16 }}>Total {money(sale.grandTotal)}</b> ({words(sale.paymentMode)})
        </div>
        <div className="sig">
          <div>
            Dispensed by: {sale.createdBy}
            {sale.pharmacistRegNo && <><br />Registered pharmacist Reg. No: {sale.pharmacistRegNo}</>}
          </div>
          <div>Authorised signatory</div>
        </div>
        <p style={{ fontSize: 11 }}>MRP is inclusive of GST. Goods once sold are not returnable except as per store policy.</p>
      </div>
      <div className="row no-print" style={{ marginTop: 12 }}>
        <button className="btn" onClick={() => window.print()}>Print invoice</button>
      </div>
    </Modal>
  );
}
