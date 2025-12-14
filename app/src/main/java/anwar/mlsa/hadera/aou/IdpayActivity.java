package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.TransferTransaction;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import anwar.mlsa.hadera.aou.domain.util.Result;
import anwar.mlsa.hadera.aou.hardware.HardwareWalletService;

public class IdpayActivity extends AppCompatActivity implements HardwareWalletService.HardwareWalletListener {

    private static final String TAG = "IdpayActivity";

    private TextInputEditText recipientIdEditText;
    private TextInputEditText amountEditText;
    private TextInputEditText memoEditText;
    private Button sendButton;
    private ProgressBar progressBar;
    private TextView balanceTextView;
    private TextView exchangeRateTextView;
    private TextInputLayout recipientLayout;
    private TextInputLayout amountLayout;
    private TextView verifiedTextView;

    private IdpayViewModel viewModel;
    private double currentBalance = 0.0;
    private double exchangeRate = 0.0;

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private HardwareWalletService hardwareWalletService;
    private boolean isHardwareWalletBound = false;
    private boolean awaitingHwConnectionForTx = false;
    private TextWatcher textWatcher;

    private final ServiceConnection hardwareWalletConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            HardwareWalletService.LocalBinder binder = (HardwareWalletService.LocalBinder) service;
            hardwareWalletService = binder.getService();
            isHardwareWalletBound = true;
            observeHardwareWalletStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isHardwareWalletBound = false;
            hardwareWalletService = null;
        }
    };

    private void observeHardwareWalletStatus() {
        if (hardwareWalletService == null) return;
        hardwareWalletService.connectionStatus.observe(this, status -> {
            if (status == HardwareWalletService.ConnectionStatus.CONNECTED && awaitingHwConnectionForTx) {
                awaitingHwConnectionForTx = false;
                Toast.makeText(this, "Device connected. Please try sending again.", Toast.LENGTH_LONG).show();
                setLoadingState(false);
            }
        });
    }

    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String scannedId = result.getData().getStringExtra("SCANNED_ID");
                    if (scannedId != null) {
                        recipientIdEditText.removeTextChangedListener(textWatcher);
                        recipientIdEditText.setText(scannedId);
                        viewModel.verifyAccountId(scannedId);
                        recipientIdEditText.addTextChangedListener(textWatcher);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> addressBookLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String selectedId = result.getData().getStringExtra("SELECTED_ACCOUNT_ID");
                    if (selectedId != null) {
                        recipientIdEditText.removeTextChangedListener(textWatcher);
                        recipientIdEditText.setText(selectedId);
                        viewModel.verifyAccountId(selectedId);
                        recipientIdEditText.addTextChangedListener(textWatcher);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.idpay);
        IdpayViewModelFactory factory = new IdpayViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(IdpayViewModel.class);
        initializeViews();
        setupToolbar();
        setupListeners();
        observeViewModel();
        loadInitialData();
        setupBiometrics();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, HardwareWalletService.class);
        bindService(intent, hardwareWalletConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isHardwareWalletBound) {
            unbindService(hardwareWalletConnection);
            isHardwareWalletBound = false;
        }
    }

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(IdpayActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                initiateTransaction();
            }
        });
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Transaction")
                .setSubtitle("Use your biometric credential to authorize the transaction")
                .setNegativeButtonText("Cancel")
                .build();
    }

    private void initiateTransaction() {
        WalletStorage.Account currentAccount = WalletStorage.getCurrentAccount(this);
        if (currentAccount == null) {
            Toast.makeText(this, "No active account found.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentAccount.isHardware) {
            if (!isHardwareWalletBound || hardwareWalletService == null) {
                Toast.makeText(this, "Hardware Wallet service is not ready. Please wait.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (hardwareWalletService.connectionStatus.getValue() == HardwareWalletService.ConnectionStatus.CONNECTED) {
                handleHardwareWalletTransaction();
            } else {
                awaitingHwConnectionForTx = true;
                setLoadingState(true);
                Toast.makeText(this, "Please connect your hardware wallet to authorize.", Toast.LENGTH_LONG).show();
                hardwareWalletService.findAndConnectToDevice();
            }
        } else {
            String recipient = safeGetText(recipientIdEditText);
            String amount = safeGetText(amountEditText);
            String memo = safeGetText(memoEditText).trim();
            viewModel.sendTransaction(recipient, amount, memo, currentBalance);
        }
    }

    private void initializeViews() {
        recipientIdEditText = findViewById(R.id.recipient_field);
        amountEditText = findViewById(R.id.amount_field);
        memoEditText = findViewById(R.id.memo_field);
        sendButton = findViewById(R.id.send_button);
        findViewById(R.id.connect_wallet_button).setVisibility(View.GONE);
        progressBar = findViewById(R.id.progressBar);
        balanceTextView = findViewById(R.id.balance_textview);
        exchangeRateTextView = findViewById(R.id.exchange_rate_text_view);
        recipientLayout = findViewById(R.id.recipient_input_layout);
        amountLayout = findViewById(R.id.amount_input_layout);
        verifiedTextView = findViewById(R.id.verified_text);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> showConfirmationDialog());

        recipientLayout.setStartIconOnClickListener(v -> {
            Intent intent = new Intent(IdpayActivity.this, AddressBookActivity.class);
            addressBookLauncher.launch(intent);
        });

        recipientLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(IdpayActivity.this, ScannerqrActivity.class);
            qrScannerLauncher.launch(intent);
        });

        textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s == recipientIdEditText.getEditableText()) {
                    verifiedTextView.setVisibility(View.GONE);
                    recipientLayout.setVisibility(View.VISIBLE);
                }
                if (viewModel != null) {
                    viewModel.onInputChanged(safeGetText(recipientIdEditText).trim(), safeGetText(amountEditText).trim(), currentBalance);
                }
            }
        };
        recipientIdEditText.addTextChangedListener(textWatcher);
        amountEditText.addTextChangedListener(textWatcher);
    }

    private void observeViewModel() {
        viewModel.isLoading().observe(this, this::setLoadingState);
        viewModel.getRecipientError().observe(this, error -> {
            recipientLayout.setError(error);
            if (error != null) {
                verifiedTextView.setVisibility(View.GONE);
                recipientLayout.setVisibility(View.VISIBLE);
            }
        });
        viewModel.getRecipientHelperText().observe(this, helperText -> recipientLayout.setHelperText(helperText));
        viewModel.getAmountError().observe(this, error -> amountLayout.setError(error));
        viewModel.isSendButtonEnabled().observe(this, isEnabled -> sendButton.setEnabled(isEnabled));
        viewModel.getVerifiedRecipient().observe(this, accountId -> {
            if (accountId != null && !accountId.isEmpty()) {
                verifiedTextView.setVisibility(View.VISIBLE);
                recipientLayout.setVisibility(View.GONE);
            } else {
                verifiedTextView.setVisibility(View.GONE);
                recipientLayout.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getTransactionResult().observe(this, result -> {
            if (result instanceof Result.Success) {
                performHapticFeedback();
                launchSuccessScreen(((Result.Success<Map<String, Object>>) result).data);
            } else if (result instanceof Result.Error) {
                Toast.makeText(this, "Transaction Failed: " + ((Result.Error) result).message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getExchangeRate().observe(this, rate -> {
            if (rate != null && !rate.equalsIgnoreCase("Error")) {
                try {
                    exchangeRate = Double.parseDouble(rate);
                    updateBalanceInUSD();
                } catch (NumberFormatException e) {
                    exchangeRateTextView.setText("Invalid rate");
                }
            } else {
                exchangeRateTextView.setText("Rate N/A");
            }
        });
    }

    private void handleHardwareWalletTransaction() {
        setLoadingState(true);
        Toast.makeText(this, "Please confirm the transaction on your hardware wallet.", Toast.LENGTH_LONG).show();
        TransferTransaction unsignedTx = viewModel.createUnsignedTransaction(
                WalletStorage.getAccountId(this),
                safeGetText(recipientIdEditText),
                safeGetText(amountEditText),
                safeGetText(memoEditText).trim()
        );
        if (unsignedTx != null) {
            hardwareWalletService.signTransaction(unsignedTx.toBytes(), this);
        } else {
            Toast.makeText(this, "Failed to create transaction.", Toast.LENGTH_SHORT).show();
            setLoadingState(false);
        }
    }

    @Override
    public void onSignatureReceived(byte[] signature) {
        Log.d(TAG, "Signature received from hardware wallet.");
        broadcastTransaction(signature);
    }

    @Override
    public void onSignatureError(Exception e) {
        runOnUiThread(() -> {
            setLoadingState(false);
            awaitingHwConnectionForTx = false;
            Log.e(TAG, "Signature error", e);
            Toast.makeText(IdpayActivity.this, "Failed to sign transaction: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void broadcastTransaction(byte[] signedTxBytes) {
        new Thread(() -> {
            try {
                Client client = Client.forTestnet();
                Transaction<?> signedTx = Transaction.fromBytes(signedTxBytes);
                TransactionResponse txResponse = signedTx.execute(client);
                TransactionReceipt receipt = txResponse.getReceipt(client);
                runOnUiThread(() -> {
                    setLoadingState(false);
                    Toast.makeText(this, "Transaction successful: " + receipt.status, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                onSignatureError(e);
            }
        }).start();
    }

    private void updateBalanceInUSD() {
        if (exchangeRate > 0) {
            double balanceInUSD = currentBalance * exchangeRate;
            String formattedBalanceInUSD = String.format(Locale.US, "$%,.2f USD", balanceInUSD);
            exchangeRateTextView.setText(formattedBalanceInUSD);
        }
    }

    private void loadInitialData() {
        currentBalance = WalletStorage.getRawBalance(this);
        balanceTextView.setText(WalletStorage.getFormattedBalance(this));
        viewModel.fetchExchangeRate();
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!isLoading);
    }

    private void showConfirmationDialog() {
        String amount = safeGetText(amountEditText);
        String recipient = safeGetText(recipientIdEditText);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_transaction_title))
                .setMessage(getString(R.string.confirm_transaction_message, amount, recipient))
                .setPositiveButton(getString(R.string.send_button_text), (dialog, which) -> biometricPrompt.authenticate(promptInfo))
                .setNegativeButton(getString(R.string.cancel_button_text), null)
                .show();
    }

    private void performHapticFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(50);
        }
    }

    private void launchSuccessScreen(java.util.Map<String, Object> responseMap) {
        Intent intent = new Intent(this, SentpayActivity.class);
        intent.putExtra("TRANSACTION_ID", String.valueOf(responseMap.get("transactionId")));
        intent.putExtra("HASHSCAN_URL", String.valueOf(responseMap.get("hashscan")));
        intent.putExtra("MEMO", safeGetText(memoEditText));
        startActivity(intent);
        finish();
    }

    private String safeGetText(TextInputEditText editText) {
        Editable text = editText.getText();
        return text != null ? text.toString() : "";
    }
}
