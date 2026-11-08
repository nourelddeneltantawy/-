/**
 * Albaraa Electronic - Smart Wallet Routing & SMS Auto-Sync Backend API
 * Node.js & Express / Firebase Cloud Functions compatible module.
 */

const express = require('express');
const path = require('path');
const app = express();
app.use(express.json());

// Serve public redirector page
app.use(express.static(path.join(__dirname, '../public')));
app.get('/pay', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/pay.html'));
});

// In-Memory / Firestore Wallets Cache
let wallets = [
  {
    id: "wallet_vf_1",
    phoneNumber: "01012345678",
    name: "محفظة فودافون كاش 1 (الرئيسية)",
    provider: "VODAFONE_CASH",
    monthlyReceiveLimit: 200000.0,
    monthlySendLimit: 200000.0,
    maxHoldingBalance: 100000.0,
    currentBalance: 12000.0,
    currentReceivedThisMonth: 15000.0,
    safetyMargin: 3000.0,
    priority: 1,
    status: "ACTIVE"
  },
  {
    id: "wallet_vf_2",
    phoneNumber: "01098765432",
    name: "محفظة فودافون كاش 2 (الاحتياطية)",
    provider: "VODAFONE_CASH",
    monthlyReceiveLimit: 200000.0,
    monthlySendLimit: 200000.0,
    maxHoldingBalance: 100000.0,
    currentBalance: 0.0,
    currentReceivedThisMonth: 0.0,
    safetyMargin: 3000.0,
    priority: 2,
    status: "ACTIVE"
  },
  {
    id: "wallet_instapay_1",
    phoneNumber: "01012345678",
    name: "حساب إنستاباي الرئيسي",
    provider: "INSTAPAY",
    monthlyReceiveLimit: 500000.0,
    monthlySendLimit: 500000.0,
    maxHoldingBalance: 500000.0,
    currentBalance: 20000.0,
    currentReceivedThisMonth: 20000.0,
    safetyMargin: 5000.0,
    priority: 1,
    status: "ACTIVE"
  }
];

/**
 * 1. Smart Routing Engine API
 * POST /api/wallet/route
 * Body: { amount: 500, provider: "VODAFONE_CASH" }
 */
app.post('/api/wallet/route', (req, res) => {
  const { amount, provider } = req.body;
  const numAmount = parseFloat(amount);

  if (!numAmount || numAmount <= 0) {
    return res.status(400).json({ success: false, message: "مبلغ غير صحيح" });
  }

  // Vodafone Cash Rules:
  // 1. (monthly_receive_limit - safety_margin - current_received_this_month) >= amount
  // 2. (max_holding_balance - current_balance) >= amount
  const candidates = wallets.filter(w => {
    if (w.status !== 'ACTIVE') return false;
    if (provider && provider !== 'ALL' && w.provider !== provider) return false;

    const monthlyAvail = (w.monthlyReceiveLimit - w.safetyMargin - w.currentReceivedThisMonth);
    const holdingAvail = (w.maxHoldingBalance - w.currentBalance);

    return monthlyAvail >= numAmount && holdingAvail >= numAmount;
  }).sort((a, b) => a.priority - b.priority);

  const selected = candidates[0];

  if (selected) {
    const phone = selected.phoneNumber;
    const ussdCode = `*9*7*${phone}*${numAmount}#`;
    const ussdDialerUri = `tel:*9*7*${phone}*${numAmount}%23`;
    const deepLink = selected.provider === 'INSTAPAY' ? 'instapay://' : '';

    return res.json({
      success: true,
      wallet: {
        id: selected.id,
        name: selected.name,
        phoneNumber: selected.phoneNumber,
        provider: selected.provider,
        priority: selected.priority
      },
      amount: numAmount,
      ussdCode,
      ussdDialerUri,
      deepLink
    });
  } else {
    return res.status(409).json({
      success: false,
      message: `لا توجد محفظة نشطة تتسع لمبلغ ${numAmount} ج.م دفعة واحدة. يرجى تجزئة المبلغ على دفعات.`
    });
  }
});

/**
 * 2. Background SMS Auto-Sync API
 * POST /api/wallet/sms-auto-sync
 * Body: { sender, messageBody, recipientSim, amount }
 */
app.post('/api/wallet/sms-auto-sync', (req, res) => {
  const { sender, messageBody, recipientSim, amount } = req.body;
  let parsedAmount = parseFloat(amount);

  // If amount not pre-parsed, parse regex from messageBody
  if (!parsedAmount && messageBody) {
    const vfRegex = /(?:تم\s*استلام\s*مبلغ|تم\s*تحويل\s*مبلغ|استلمت\s*مبلغ|إيداع\s*بمبلغ)\s*([\d,.]+)/i;
    const match = vfRegex.exec(messageBody);
    if (match) {
      parsedAmount = parseFloat(match[1].replace(/,/g, ''));
    }
  }

  if (!parsedAmount || parsedAmount <= 0) {
    return res.status(400).json({ success: false, message: "تعذر استخراج المبلغ" });
  }

  // Find target wallet by recipient phone number or active priority
  let target = wallets.find(w => recipientSim && w.phoneNumber.includes(recipientSim.slice(-8)));
  if (!target) {
    target = wallets.find(w => w.status === 'ACTIVE') || wallets[0];
  }

  if (target) {
    target.currentReceivedThisMonth += parsedAmount;
    target.currentBalance += parsedAmount;

    // Check holding capacity
    if (target.currentBalance >= target.maxHoldingBalance) {
      target.status = 'HOLDING_MAX_REACHED';
      console.warn(`[WALLET ALERT] المحفظة (${target.name}) وصلت للحد الأقصى للرصيد الحجمي (${target.maxHoldingBalance} ج). تم تحويل حالتها إلى HOLDING_MAX_REACHED لحين سحب الرصيد.`);
    } else if ((target.monthlyReceiveLimit - target.safetyMargin - target.currentReceivedThisMonth) <= 0) {
      target.status = 'FULL';
    }

    return res.json({
      success: true,
      message: `تم تسجيل تحويل ${parsedAmount} ج.م بنجاح`,
      wallet: target
    });
  }

  return res.status(404).json({ success: false, message: "لم يتم العثور على محفظة مطابقة" });
});

/**
 * 3. Withdraw Holding Balance
 * POST /api/wallet/withdraw
 * Body: { walletId }
 */
app.post('/api/wallet/withdraw', (req, res) => {
  const { walletId } = req.body;
  const target = wallets.find(w => w.id === walletId);
  if (!target) {
    return res.status(404).json({ success: false, message: "المحفظة غير موجودة" });
  }

  target.currentBalance = 0.0;
  if (target.status === 'HOLDING_MAX_REACHED') {
    target.status = 'ACTIVE';
  }

  return res.json({
    success: true,
    message: `تم تصفير الرصيد الفعلي للمحفظة (${target.name}) وإعادة تنشيطها فوراً!`,
    wallet: target
  });
});

const PORT = process.env.PORT || 3000;
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`Smart Wallet Routing API running on port ${PORT}`);
  });
}

module.exports = app;
