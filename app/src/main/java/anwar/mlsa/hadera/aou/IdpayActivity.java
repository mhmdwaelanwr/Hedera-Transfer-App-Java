package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import anwar.mlsa.hadera.aou.domain.util.Result;

public class IdpayActivity extends AppCompatActivity {

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


    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.hasExtra("SCANNED_ID")) {
                        String scannedId = data.getStringExtra("SCANNED_ID");
                        recipientIdEditText.setText(scannedId);
                        if (viewModel != null) {
                            viewModel.verifyAccountId(scannedId);
                        }
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

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(IdpayActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                String recipient = safeGetText(recipientIdEditText);
                String amount = safeGetText(amountEditText);
                String memo = safeGetText(memoEditText).trim();
                viewModel.sendTransaction(recipient, amount, memo, currentBalance);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric login for my app")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use account password")
                .build();
    }


    private void initializeViews() {
        recipientIdEditText = findViewById(R.id.recipient_field);
        amountEditText = findViewById(R.id.amount_field);
        memoEditText = findViewById(R.id.memo_field);
        sendButton = findViewById(R.id.send_button);
        progressBar = findViewById(R.id.progressBar);
        balanceTextView = findViewById(R.id.balance_textview);
        exchangeRateTextView = findViewById(R.id.exchange_rate_text_view);
        recipientLayout = findViewById(R.id.recipient_input_layout);
        amountLayout = findViewById(R.id.amount_input_layout);
        verifiedTextView = findViewById(R.id.verified_text);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> showConfirmationDialog());

        recipientLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(IdpayActivity.this, ScannerqrActivity.class);
            qrScannerLauncher.launch(intent);
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (viewModel != null) {
                    viewModel.onInputChanged(
                            safeGetText(recipientIdEditText).trim(),
                            safeGetText(amountEditText).trim(),
                            currentBalance
                    );
                }
            }
        };

        recipientIdEditText.addTextChangedListener(textWatcher);
        amountEditText.addTextChangedListener(textWatcher);
    }

    private void observeViewModel() {
        viewModel.isLoading().observe(this, this::setLoadingState);
        viewModel.getRecipientError().observe(this, error -> recipientLayout.setError(error));
        viewModel.getAmountError().observe(this, error -> amountLayout.setError(error));
        viewModel.isSendButtonEnabled().observe(this, isEnabled -> sendButton.setEnabled(isEnabled));

        viewModel.getVerifiedRecipient().observe(this, recipientId -> {
            recipientLayout.setVisibility(View.GONE);
            verifiedTextView.setVisibility(View.VISIBLE);
            verifiedTextView.setText(getString(R.string.verified_recipient, recipientId));
        });

        viewModel.getTransactionResult().observe(this, result -> {
            if (result instanceof Result.Success) {
                performHapticFeedback();
                launchSuccessScreen(((Result.Success<Map<String, Object>>) result).data);
            } else if (result instanceof Result.Error) {
                String errorMessage = ((Result.Error<Map<String, Object>>) result).message;
                Toast.makeText(this, "Transaction Failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getExchangeRate().observe(this, rate -> {
            if (rate != null) {
                try {
                    String[] parts = rate.split("=");
                    if (parts.length > 1) {
                        String rateValueString = parts[1].replace("$", "").trim();
                        exchangeRate = Double.parseDouble(rateValueString);
                        updateBalanceInUSD();
                    }
                } catch (NumberFormatException e) {
                    exchangeRateTextView.setText(rate);
                }
            }
        });
    }

    private void updateBalanceInUSD() {
        if (exchangeRate > 0) {
            double balanceInUSD = currentBalance * exchangeRate;
            String formattedBalanceInUSD = String.format(Locale.US, "$%,.2f", balanceInUSD);
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
        recipientIdEditText.setEnabled(!isLoading);
        amountEditText.setEnabled(!isLoading);
        memoEditText.setEnabled(!isLoading);
    }

    private void showConfirmationDialog() {
        String amount = safeGetText(amountEditText);
        String recipient = safeGetText(recipientIdEditText);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_transaction_title))
                .setMessage(getString(R.string.confirm_transaction_message, amount, recipient))
                .setPositiveButton(getString(R.string.send_button_text), (dialog, which) -> {
                    biometricPrompt.authenticate(promptInfo);
                })
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
