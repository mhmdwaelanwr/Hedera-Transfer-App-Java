# Buy/Sell Integration Guide

## Overview
This document outlines how to integrate third-party fiat-to-crypto on/off-ramp solutions (e.g., MoonPay, Wyre, Transak) into the Hedera Transfer App to enable users to buy and sell HBAR directly within the application.

## Integration Options

### 1. MoonPay Integration

**Website:** https://www.moonpay.com/

**Integration Method:**
- Web SDK or Direct API
- WebView-based flow or deep linking

**Key Steps:**
1. Sign up for a MoonPay account and obtain API keys
2. Implement MoonPay SDK or WebView integration
3. Handle callbacks for transaction status
4. Update wallet balance after successful purchase

**Code Example (WebView approach):**
```java
// In TransferActivity or a new BuySellActivity
private void openMoonPayBuy() {
    String accountId = WalletStorage.getAccountId(this);
    String apiKey = "YOUR_MOONPAY_API_KEY";
    String baseUrl = "https://buy.moonpay.com";
    
    Uri uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("apiKey", apiKey)
            .appendQueryParameter("currencyCode", "hbar")
            .appendQueryParameter("walletAddress", accountId)
            .build();
    
    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    startActivity(intent);
    
    // Alternative: Use WebView for in-app experience
    // showMoonPayWebView(uri.toString());
}
```

### 2. Wyre Integration

**Website:** https://www.sendwyre.com/

**Integration Method:**
- REST API
- Widget or redirect flow

**Key Steps:**
1. Create Wyre account and get API credentials
2. Generate payment orders via API
3. Redirect user to Wyre payment page
4. Handle webhooks for order completion
5. Refresh balance in the app

**Code Example:**
```java
private void openWyreBuy() {
    String accountId = WalletStorage.getAccountId(this);
    String dest = "hedera:" + accountId;
    String redirectUrl = "hederawallet://buy-complete";
    
    // Call Wyre API to create order
    // Then open the payment URL
    String wyreUrl = "https://pay.sendwyre.com/purchase?dest=" + dest;
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(wyreUrl));
    startActivity(intent);
}
```

### 3. Transak Integration

**Website:** https://transak.com/

**Integration Method:**
- Widget/SDK
- Simple redirect with parameters

**Key Steps:**
1. Register with Transak and get API key
2. Configure supported cryptocurrencies (HBAR)
3. Embed Transak widget or redirect
4. Handle completion callbacks

**Code Example:**
```java
private void openTransakBuy() {
    String accountId = WalletStorage.getAccountId(this);
    String apiKey = "YOUR_TRANSAK_API_KEY";
    
    String url = String.format(
        "https://global.transak.com/?apiKey=%s&cryptoCurrencyCode=HBAR&walletAddress=%s&defaultPaymentMethod=credit_debit_card",
        apiKey, accountId
    );
    
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    startActivity(intent);
}
```

## UI Integration Points

### Add Buy/Sell Buttons to TransferActivity

**Location:** `app/src/main/res/layout/transfer.xml`

Add buttons after the Send/Receive buttons:

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/buy_hbar_button"
        style="?attr/materialButtonOutlinedStyle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginEnd="8dp"
        android:layout_weight="1"
        android:text="Buy HBAR"
        android:fontFamily="@font/segoe_ui_semibold"
        app:icon="@drawable/ic_add" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/sell_hbar_button"
        style="?attr/materialButtonOutlinedStyle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_weight="1"
        android:text="Sell HBAR"
        android:fontFamily="@font/segoe_ui_semibold"
        app:icon="@drawable/ic_remove" />
</LinearLayout>
```

### Implement Click Handlers

**Location:** `app/src/main/java/anwar/mlsa/hadera/aou/TransferActivity.java`

```java
private void setupBuySellButtons() {
    binding.buyHbarButton.setOnClickListener(v -> {
        VibrationManager.vibrate(this);
        showBuyOptions();
    });
    
    binding.sellHbarButton.setOnClickListener(v -> {
        VibrationManager.vibrate(this);
        showSellOptions();
    });
}

private void showBuyOptions() {
    String[] options = {"MoonPay", "Wyre", "Transak"};
    new AlertDialog.Builder(this)
            .setTitle("Choose Payment Provider")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        openMoonPayBuy();
                        break;
                    case 1:
                        openWyreBuy();
                        break;
                    case 2:
                        openTransakBuy();
                        break;
                }
            })
            .show();
}
```

## Handling Callbacks

### Deep Link Configuration

Add deep link handling in `AndroidManifest.xml`:

```xml
<activity android:name=".TransferActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="hederawallet" android:host="buy-complete" />
    </intent-filter>
</activity>
```

Handle the callback in TransferActivity:

```java
@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (intent != null && intent.getData() != null) {
        Uri data = intent.getData();
        if ("buy-complete".equals(data.getHost())) {
            // Refresh balance
            updateUI();
            Toast.makeText(this, "Purchase complete! Balance updating...", Toast.LENGTH_LONG).show();
        }
    }
}
```

## Security Considerations

1. **Never store API keys in code** - Use BuildConfig or secure storage
2. **Validate all callbacks** - Verify transaction signatures/IDs
3. **Use HTTPS only** - All API calls must be secure
4. **Handle errors gracefully** - Provide clear feedback to users
5. **Test in sandbox** - Use provider test environments first

## Testing

1. Use sandbox/test API keys during development
2. Test with small amounts first
3. Verify balance updates correctly
4. Test error scenarios (failed payments, timeouts)
5. Test on both Android versions (old and new)

## Next Steps

1. Choose a primary provider (recommend MoonPay or Transak)
2. Register and obtain API keys
3. Implement the integration following this guide
4. Test thoroughly in sandbox environment
5. Submit for provider approval if required
6. Deploy to production

## Resources

- MoonPay Documentation: https://docs.moonpay.com/
- Wyre Documentation: https://docs.sendwyre.com/
- Transak Documentation: https://docs.transak.com/
- Hedera Documentation: https://docs.hedera.com/
