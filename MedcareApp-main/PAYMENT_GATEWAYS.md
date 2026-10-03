# Appointment billing and payment gateways

Booking an appointment creates an INR invoice. Staff can record a received cash, card, UPI, or bank-transfer payment. Gateway payments remain pending until the provider response is verified by the backend.

Configure only the gateways the hospital has merchant accounts for. Keep all credentials in the backend environment; never put secret keys in the React app or commit them.

| Environment variable | Purpose |
| --- | --- |
| `PAYMENTS_RAZORPAY_KEY_ID` | Razorpay public key ID |
| `PAYMENTS_RAZORPAY_KEY_SECRET` | Razorpay server-side secret |
| `PAYMENTS_STRIPE_SECRET_KEY` | Stripe server-side secret key |
| `PAYMENTS_PAYU_KEY` | PayU merchant key |
| `PAYMENTS_PAYU_SALT` | PayU merchant salt |
| `PAYMENTS_PAYU_MODE` | Set to `test` (default) or `live` |
| `PAYMENTS_PUBLIC_BACKEND_URL` | Public backend origin used by PayU return URLs |
| `PAYMENTS_FRONTEND_URL` | Frontend origin used by Stripe and PayU redirects |

The billing screen only displays providers whose required credentials are configured. Razorpay signatures are verified server-side, Stripe Checkout sessions are re-fetched from Stripe before being marked paid, and PayU signed return values are verified before invoice updates. For PayU production use, the backend return URL must be publicly reachable over HTTPS.
