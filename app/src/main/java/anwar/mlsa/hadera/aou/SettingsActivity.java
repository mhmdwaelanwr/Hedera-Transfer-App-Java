package anwar.mlsa.hadera.aou;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SettingsActivity extends AppCompatActivity {

    private static final String NOTIFICATION_WORK_TAG = "transaction_notification_work";
    private static final String PREFS_NAME = "SettingsPrefs";
    private static final String KEY_EXPORT_PATH = "EXPORT_PATH";
    private static final String EXPORT_HISTORY_TAG = "export_history_tag";

    private RecyclerView accountsRecyclerView;
    private AccountAdapter accountAdapter;
    private SwitchMaterial notificationsSwitch;
    private SwitchMaterial hapticFeedbackSwitch;
    private Button exportHistoryButton;
    private ImageButton changeExportLocationButton;
    private RadioGroup exportFormatRadioGroup;
    private ProgressBar exportProgressBar;
    private Uri exportLocation;

    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkListener;

    private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.getData() != null) {
                        exportLocation = data.getData();
                        getContentResolver().takePersistableUriPermission(exportLocation,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putString(KEY_EXPORT_PATH, exportLocation.toString())
                                .apply();
                        Toast.makeText(this, "Export location updated", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        setupToolbar();
        loadExportLocation();
        initializeViews();
        setupNetworkListener();
        setupThemeButtons();
        setupAccountButtons();
        setupRecyclerView();
        setupNotificationSwitch();
        setupHapticFeedbackSwitch();
        setupExportButton();
        setupSourceCodeLink();
        setupDeveloperInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountList();
    }

    private void initializeViews() {
        accountsRecyclerView = findViewById(R.id.accounts_recyclerview);
        notificationsSwitch = findViewById(R.id.notifications_switch);
        hapticFeedbackSwitch = findViewById(R.id.haptic_feedback_switch);
        exportHistoryButton = findViewById(R.id.export_history_button);
        changeExportLocationButton = findViewById(R.id.change_export_location_button);
        exportFormatRadioGroup = findViewById(R.id.export_format_radiogroup);
        exportProgressBar = findViewById(R.id.export_progress_bar);
        
        // Features buttons
        Button addressBookButton = findViewById(R.id.address_book_button);
        Button priceAlertsButton = findViewById(R.id.price_alerts_button);
        
        addressBookButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, AddressBookActivity.class));
        });
        
        priceAlertsButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, PriceAlertsActivity.class));
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadExportLocation() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String pathString = prefs.getString(KEY_EXPORT_PATH, null);
        if (pathString != null) {
            exportLocation = Uri.parse(pathString);
        }
    }

    private void setupExportButton() {
        exportHistoryButton.setOnClickListener(v -> fetchHistoryForExport());
        changeExportLocationButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            directoryPickerLauncher.launch(intent);
        });
    }

    private void fetchHistoryForExport() {
        String accountId = WalletStorage.getAccountId(this);
        if (accountId == null || accountId.isEmpty()) {
            Toast.makeText(this, "No active account found.", Toast.LENGTH_SHORT).show();
            return;
        }

        exportProgressBar.setVisibility(View.VISIBLE);
        exportHistoryButton.setEnabled(false);

        String url = "https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=" + accountId + "&limit=1000";
        networkReq.startRequestNetwork(RequestNetworkController.GET, url, EXPORT_HISTORY_TAG, networkListener);
    }

    private void setupNetworkListener() {
        networkReq = new RequestNetwork(this);
        networkListener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if (EXPORT_HISTORY_TAG.equals(tag)) {
                    exportProgressBar.setVisibility(View.GONE);
                    exportHistoryButton.setEnabled(true);

                    HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(response, WalletStorage.getAccountId(SettingsActivity.this));
                    if (historyResponse.transactions.isEmpty()) {
                        Toast.makeText(SettingsActivity.this, "No transaction history found online.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    proceedWithExport(historyResponse.transactions);
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                if (EXPORT_HISTORY_TAG.equals(tag)) {
                    exportProgressBar.setVisibility(View.GONE);
                    exportHistoryButton.setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Failed to fetch history: " + message, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    private void proceedWithExport(ArrayList<Transaction> transactions) {
        int selectedId = exportFormatRadioGroup.getCheckedRadioButtonId();
        String fileName, mimeType, content;

        if (selectedId == R.id.export_format_json) {
            fileName = "hadera-history-export.json";
            mimeType = "application/json";
            content = new Gson().toJson(transactions);
        } else if (selectedId == R.id.export_format_log) {
            fileName = "hadera-history-export.log";
            mimeType = "text/plain";
            content = getLogFormattedHistory(transactions);
        } else if (selectedId == R.id.export_format_csv) {
            fileName = "hadera-history-export.csv";
            mimeType = "text/csv";
            content = getCsvFormattedHistory(transactions);
        } else {
            Toast.makeText(this, "Please select an export format.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (exportLocation != null) {
            writeContentToUri(exportLocation, fileName, mimeType, content);
        } else {
            Toast.makeText(this, "Please set an export location first.", Toast.LENGTH_LONG).show();
        }
    }

    private void writeContentToUri(Uri treeUri, String fileName, String mimeType, String content) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(this, treeUri);
            DocumentFile file = directory.findFile(fileName);
            if (file != null) {
                file.delete();
            }
            file = directory.createFile(mimeType, fileName);

            try (OutputStream os = getContentResolver().openOutputStream(file.getUri())) {
                os.write(content.getBytes());
                Toast.makeText(this, "History exported successfully.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error accessing export location. Please select it again.", Toast.LENGTH_LONG).show();
        }
    }

    private String getLogFormattedHistory(ArrayList<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();
        for (Transaction t : transactions) {
            sb.append("Type: ").append(t.type).append("\n");
            sb.append("Date: ").append(t.date).append("\n");
            sb.append("Amount: ").append(t.amount).append("\n");
            sb.append("Party: ").append(t.party).append("\n");
            sb.append("Status: ").append(t.status).append("\n");
            if (t.fee != null) {
                sb.append("Fee: ").append(t.fee).append("\n");
            }
            if (t.memo != null && !t.memo.isEmpty()) {
                sb.append("Memo: ").append(t.memo).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String getCsvFormattedHistory(ArrayList<Transaction> transactions) {
        return "Date,Party,Amount,Memo,Type,Status,Fee\n" +
                transactions.stream()
                        .map(t -> t.date + "," + t.party + "," + t.amount + "," + t.memo + "," + t.type + "," + t.status + "," + t.fee)
                        .collect(Collectors.joining("\n"));
    }

    private void setupThemeButtons() {
        findViewById(R.id.light_mode_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            ThemeManager.saveTheme(this, AppCompatDelegate.MODE_NIGHT_NO);
        });

        findViewById(R.id.dark_mode_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            ThemeManager.saveTheme(this, AppCompatDelegate.MODE_NIGHT_YES);
        });
    }

    private void setupSourceCodeLink() {
        findViewById(R.id.source_code_card).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mhmdwaelanwr/Hedera-Transfer-App-Java"));
            startActivity(intent);
        });
    }

    private void setupDeveloperInfo() {
        findViewById(R.id.developer_info).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://linkedin.com/in/mhmdwaelanwr"));
            startActivity(intent);
        });
    }

    private void setupNotificationSwitch() {
        notificationsSwitch.setChecked(NotificationManager.areNotificationsEnabled(this));
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            VibrationManager.vibrate(this);
            NotificationManager.setNotificationsEnabled(this, isChecked);
            if (isChecked) {
                startNotificationWorker();
            } else {
                stopNotificationWorker();
            }
        });
    }

    private void setupHapticFeedbackSwitch() {
        hapticFeedbackSwitch.setChecked(VibrationManager.isVibrationEnabled(this));
        hapticFeedbackSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            VibrationManager.vibrate(this);
            VibrationManager.setVibrationEnabled(this, isChecked);
        });
    }

    private void startNotificationWorker() {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(TransactionNotificationWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(NOTIFICATION_WORK_TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
    }

    private void stopNotificationWorker() {
        WorkManager.getInstance(this).cancelUniqueWork(NOTIFICATION_WORK_TAG);
    }

    private void setupAccountButtons() {
        findViewById(R.id.add_account_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            if (WalletStorage.canAddAccount(this)) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                Toast.makeText(this, "You have reached the maximum number of accounts.", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.logout_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            new AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to log out from all accounts? This will clear all app data.")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        WalletStorage.logout(this);
                        stopNotificationWorker();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupRecyclerView() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        accountAdapter = new AccountAdapter(new ArrayList<>());
        accountsRecyclerView.setAdapter(accountAdapter);
    }

    private void updateAccountList() {
        List<WalletStorage.Account> accounts = WalletStorage.getAccounts(this);
        accountAdapter.updateData(accounts);
    }

    private void restartToTransferActivity() {
        Intent intent = new Intent(this, TransferActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountViewHolder> {
        private List<WalletStorage.Account> accounts;

        public AccountAdapter(List<WalletStorage.Account> accounts) {
            this.accounts = accounts;
        }

        public void updateData(List<WalletStorage.Account> newAccounts) {
            this.accounts = newAccounts;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AccountViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.account_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
            holder.bind(accounts.get(position), position);
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }

        class AccountViewHolder extends RecyclerView.ViewHolder {
            TextView accountIdTextView, currentAccountIndicator;
            Button switchAccountButton, deleteAccountButton;

            AccountViewHolder(@NonNull View itemView) {
                super(itemView);
                accountIdTextView = itemView.findViewById(R.id.account_id_textview);
                switchAccountButton = itemView.findViewById(R.id.switch_account_button);
                deleteAccountButton = itemView.findViewById(R.id.delete_account_button);
                currentAccountIndicator = itemView.findViewById(R.id.current_account_indicator);
            }

            void bind(final WalletStorage.Account account, final int position) {
                accountIdTextView.setText(account.getAccountId());

                if (position == WalletStorage.getCurrentAccountIndex(SettingsActivity.this)) {
                    currentAccountIndicator.setVisibility(View.VISIBLE);
                    switchAccountButton.setVisibility(View.GONE);
                } else {
                    currentAccountIndicator.setVisibility(View.GONE);
                    switchAccountButton.setVisibility(View.VISIBLE);
                    switchAccountButton.setOnClickListener(v -> {
                        WalletStorage.setCurrentAccountIndex(SettingsActivity.this, position);
                        restartToTransferActivity();
                    });
                }

                deleteAccountButton.setOnClickListener(v -> {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("Delete Account")
                            .setMessage("Are you sure you want to delete this account?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                boolean isDeletingCurrent = (getAdapterPosition() == WalletStorage.getCurrentAccountIndex(SettingsActivity.this));
                                WalletStorage.deleteAccount(SettingsActivity.this, getAdapterPosition());
                                if (WalletStorage.getAccounts(SettingsActivity.this).isEmpty()) {
                                    Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                } else if (isDeletingCurrent) {
                                    restartToTransferActivity();
                                } else {
                                    updateAccountList();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }
        }
    }
}
