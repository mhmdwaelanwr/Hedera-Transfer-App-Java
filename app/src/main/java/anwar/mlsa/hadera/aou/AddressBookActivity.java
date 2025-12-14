package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class AddressBookActivity extends AppCompatActivity {

    private RecyclerView contactsRecyclerView;
    private ContactAdapter adapter;
    private TextView emptyView;
    private List<WalletStorage.Contact> contactList = new ArrayList<>();
    private ActivityResultLauncher<Intent> qrScannerLauncher;
    private IdpayViewModel viewModel;

    // LiveData to communicate scanned QR result to the dialog
    private final MutableLiveData<String> scannedIdResult = new MutableLiveData<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_book);

        IdpayViewModelFactory factory = new IdpayViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(IdpayViewModel.class);

        setupToolbar();
        initializeViews();
        setupRecyclerView();
        setupFab();
        loadContacts();
        setupQrScanner();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.address_book_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        contactsRecyclerView = findViewById(R.id.contacts_recyclerview);
        emptyView = findViewById(R.id.empty_address_book_text);
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter(contactList,
                this::onContactSelected,
                this::onDeleteContact
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(adapter);
    }

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fab_add_contact);
        fab.setOnClickListener(view -> showAddContactDialog());
    }

    private void loadContacts() {
        contactList.clear();
        contactList.addAll(WalletStorage.getContacts(this));
        adapter.notifyDataSetChanged();
        checkIfEmpty();
    }

    private void checkIfEmpty() {
        if (contactList.isEmpty()) {
            contactsRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            contactsRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void onContactSelected(WalletStorage.Contact contact) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SELECTED_ACCOUNT_ID", contact.getAccountId());
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void onDeleteContact(WalletStorage.Contact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete '" + contact.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    WalletStorage.deleteContact(this, contact);
                    loadContacts();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupQrScanner() {
        qrScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String scannedId = result.getData().getStringExtra("SCANNED_ID");
                        scannedIdResult.postValue(scannedId);
                    }
                }
        );
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_contact, null);
        final EditText nameEditText = view.findViewById(R.id.edit_text_name);
        final EditText accountIdEditText = view.findViewById(R.id.edit_text_account_id);
        final TextInputLayout accountIdLayout = view.findViewById(R.id.account_id_layout);
        final TextView verifiedTextView = view.findViewById(R.id.verified_text_dialog);

        // --- Text Watcher for manual input ---
        final TextWatcher dialogTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                // Trigger manual validation flow in ViewModel
                viewModel.onRecipientInputChanged(s.toString().trim(), "0", 0);
            }
        };
        accountIdEditText.addTextChangedListener(dialogTextWatcher);

        // --- QR Scanner setup ---
        accountIdLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(AddressBookActivity.this, ScannerqrActivity.class);
            qrScannerLauncher.launch(intent);
        });

        // --- Observers for UI updates ---
        final Observer<String> verifiedObserver = accountId -> {
            if (accountId != null && !accountId.isEmpty()) {
                verifiedTextView.setVisibility(View.VISIBLE);
                accountIdLayout.setVisibility(View.GONE);
                // Set the text without triggering the watcher to prevent loops
                accountIdEditText.removeTextChangedListener(dialogTextWatcher);
                accountIdEditText.setText(accountId);
                accountIdEditText.addTextChangedListener(dialogTextWatcher);
            } else {
                verifiedTextView.setVisibility(View.GONE);
                accountIdLayout.setVisibility(View.VISIBLE);
            }
        };

        final Observer<String> errorObserver = accountIdLayout::setError;
        final Observer<String> helperObserver = accountIdLayout::setHelperText;

        // Observer for the QR result from the launcher
        final Observer<String> qrResultObserver = scannedId -> {
            if (scannedId != null) {
                viewModel.verifyAccountId(scannedId); // Trigger verified (QR) flow
                scannedIdResult.setValue(null); // Consume event
            }
        };

        viewModel.getVerifiedRecipient().observe(this, verifiedObserver);
        viewModel.getRecipientError().observe(this, errorObserver);
        viewModel.getRecipientHelperText().observe(this, helperObserver);
        scannedIdResult.observe(this, qrResultObserver);

        // Reset ViewModel state for the new dialog
        viewModel.onRecipientInputChanged("", "0", 0);

        builder.setView(view)
                .setTitle("Add New Contact")
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameEditText.getText().toString().trim();
                    String accountId = accountIdEditText.getText().toString().trim();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(accountId)) {
                        Toast.makeText(this, "Name and Account ID cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!accountId.matches("^0\\.0\\.[0-9]{7}$")) {
                        Toast.makeText(this, "Invalid Account ID format. Expected 0.0.XXXXXXX", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (WalletStorage.addContact(this, name, accountId)) {
                        Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
                        loadContacts();
                    } else {
                        Toast.makeText(this, "A contact with this Account ID already exists", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            // Clean up observers to prevent leaks and multi-triggering
            viewModel.getVerifiedRecipient().removeObserver(verifiedObserver);
            viewModel.getRecipientError().removeObserver(errorObserver);
            viewModel.getRecipientHelperText().removeObserver(helperObserver);
            scannedIdResult.removeObserver(qrResultObserver);
        });
        dialog.show();
    }

    // Adapter Class
    private static class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
        private final List<WalletStorage.Contact> contacts;
        private final OnContactClickListener clickListener;
        private final OnDeleteClickListener deleteClickListener;

        interface OnContactClickListener {
            void onContactClick(WalletStorage.Contact contact);
        }

        interface OnDeleteClickListener {
            void onDeleteClick(WalletStorage.Contact contact);
        }

        ContactAdapter(List<WalletStorage.Contact> contacts, OnContactClickListener clickListener, OnDeleteClickListener deleteClickListener) {
            this.contacts = contacts;
            this.clickListener = clickListener;
            this.deleteClickListener = deleteClickListener;
        }

        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_item, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            holder.bind(contacts.get(position), clickListener, deleteClickListener);
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

        static class ContactViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView, accountIdTextView;
            ImageButton deleteButton;

            ContactViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.contact_name);
                accountIdTextView = itemView.findViewById(R.id.contact_account_id);
                deleteButton = itemView.findViewById(R.id.delete_contact_button);
            }

            void bind(final WalletStorage.Contact contact, final OnContactClickListener clickListener, final OnDeleteClickListener deleteClickListener) {
                nameTextView.setText(contact.getName());
                accountIdTextView.setText(contact.getAccountId());
                itemView.setOnClickListener(v -> clickListener.onContactClick(contact));
                deleteButton.setOnClickListener(v -> deleteClickListener.onDeleteClick(contact));
            }
        }
    }
}
