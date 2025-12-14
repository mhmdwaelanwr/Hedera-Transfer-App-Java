package anwar.mlsa.hadera.aou;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WalletStorage {

    private static final String PREF_NAME = "EncryptedWalletData";
    private static final String KEY_ACCOUNTS = "ACCOUNTS";
    private static final String KEY_CONTACTS = "CONTACTS";
    private static final String KEY_CURRENT_ACCOUNT_INDEX = "CURRENT_ACCOUNT_INDEX";
    private static final int MAX_ACCOUNTS = 6;

    private static final String SUFFIX_FORMATTED_BALANCE = "_FORMATTED_BALANCE";
    private static final String SUFFIX_RAW_BALANCE = "_RAW_BALANCE";
    private static final String SUFFIX_TRANSACTION_HISTORY = "_TRANSACTION_HISTORY";
    private static final String SUFFIX_HISTORY_MIGRATED = "_HISTORY_MIGRATED";

    private static SharedPreferences getPrefs(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e("WalletStorage", "Could not create encrypted shared preferences", e);
            throw new RuntimeException("Could not create encrypted shared preferences", e);
        }
    }

    // --- Account Management ---
    public static List<Account> getAccounts(Context context) {
        String json = getPrefs(context).getString(KEY_ACCOUNTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Account>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    public static void saveAccounts(Context context, List<Account> accounts) {
        getPrefs(context).edit().putString(KEY_ACCOUNTS, new Gson().toJson(accounts)).apply();
    }

    public static boolean canAddAccount(Context context) {
        return getAccounts(context).size() < MAX_ACCOUNTS;
    }

    public static boolean addAccount(Context context, String accountId, String privateKey) {
        return addAccount(context, accountId, privateKey, false);
    }

    public static boolean addHardwareAccount(Context context, String accountId) {
        return addAccount(context, accountId, "", true);
    }

    private static boolean addAccount(Context context, String accountId, String privateKey, boolean isHardware) {
        List<Account> accounts = getAccounts(context);
        if (!canAddAccount(context)) return false;
        for (Account account : accounts) {
            if (account.getAccountId().equals(accountId)) return false;
        }
        accounts.add(new Account(accountId, privateKey, isHardware));
        saveAccounts(context, accounts);
        if (accounts.size() == 1) setCurrentAccountIndex(context, 0);
        return true;
    }

    public static void deleteAccount(Context context, int index) {
        List<Account> accounts = getAccounts(context);
        if (index >= 0 && index < accounts.size()) {
            String accountId = accounts.get(index).getAccountId();
            SharedPreferences.Editor editor = getPrefs(context).edit();
            editor.remove(accountId + SUFFIX_FORMATTED_BALANCE);
            editor.remove(accountId + SUFFIX_RAW_BALANCE);
            editor.remove(accountId + SUFFIX_TRANSACTION_HISTORY);
            editor.remove(accountId + SUFFIX_HISTORY_MIGRATED);
            accounts.remove(index);
            saveAccounts(context, accounts);

            int currentIdx = getCurrentAccountIndex(context);
            if (currentIdx == index) {
                setCurrentAccountIndex(context, accounts.isEmpty() ? -1 : 0);
            } else if (currentIdx > index) {
                setCurrentAccountIndex(context, currentIdx - 1);
            }
        }
    }

    public static void setCurrentAccountIndex(Context context, int index) {
        getPrefs(context).edit().putInt(KEY_CURRENT_ACCOUNT_INDEX, index).apply();
    }

    public static int getCurrentAccountIndex(Context context) {
        return getPrefs(context).getInt(KEY_CURRENT_ACCOUNT_INDEX, -1);
    }

    public static Account getCurrentAccount(Context context) {
        int index = getCurrentAccountIndex(context);
        if (index != -1) {
            List<Account> accounts = getAccounts(context);
            if (index < accounts.size()) return accounts.get(index);
        }
        return null;
    }

    public static boolean isWalletSaved(Context context) {
        return getCurrentAccount(context) != null;
    }

    public static String getAccountId(Context context) {
        Account currentAccount = getCurrentAccount(context);
        return (currentAccount != null) ? currentAccount.getAccountId() : null;
    }

    public static String getPrivateKey(Context context) {
        Account currentAccount = getCurrentAccount(context);
        if (currentAccount == null || currentAccount.isHardware) return null;
        return currentAccount.getPrivateKey();
    }

    // --- Contact Management ---
    public static List<Contact> getContacts(Context context) {
        String json = getPrefs(context).getString(KEY_CONTACTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Contact>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    public static void saveContacts(Context context, List<Contact> contacts) {
        getPrefs(context).edit().putString(KEY_CONTACTS, new Gson().toJson(contacts)).apply();
    }

    public static boolean addContact(Context context, String name, String accountId) {
        List<Contact> contacts = getContacts(context);
        for (Contact contact : contacts) {
            if (contact.getAccountId().equals(accountId)) return false;
        }
        contacts.add(new Contact(name, accountId));
        saveContacts(context, contacts);
        return true;
    }

    public static void deleteContact(Context context, Contact contactToDelete) {
        List<Contact> contacts = getContacts(context);
        contacts.removeIf(contact -> contact.getAccountId().equals(contactToDelete.getAccountId()));
        saveContacts(context, contacts);
    }

    // --- Balance Management ---
    public static void saveFormattedBalance(Context context, String formattedBalance) {
        String accountId = getAccountId(context);
        if (accountId != null) {
            getPrefs(context).edit().putString(accountId + SUFFIX_FORMATTED_BALANCE, formattedBalance).apply();
        }
    }

    public static String getFormattedBalance(Context context) {
        String accountId = getAccountId(context);
        return (accountId != null) ? getPrefs(context).getString(accountId + SUFFIX_FORMATTED_BALANCE, "0.00 ℏ") : "0.00 ℏ";
    }

    public static void saveRawBalance(Context context, double rawBalance) {
        String accountId = getAccountId(context);
        if (accountId != null) {
            getPrefs(context).edit().putLong(accountId + SUFFIX_RAW_BALANCE, Double.doubleToRawLongBits(rawBalance)).apply();
        }
    }

    public static double getRawBalance(Context context) {
        String accountId = getAccountId(context);
        return (accountId != null) ? Double.longBitsToDouble(getPrefs(context).getLong(accountId + SUFFIX_RAW_BALANCE, 0L)) : 0.0;
    }

    // --- History Management ---
    public static ArrayList<Transaction> getHistory(Context context) {
        String accountId = getAccountId(context);
        if (accountId == null) return new ArrayList<>();
        String json = getPrefs(context).getString(accountId + SUFFIX_TRANSACTION_HISTORY, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type type = new TypeToken<ArrayList<Transaction>>() {}.getType();
            return new Gson().fromJson(json, type);
        } catch (JsonSyntaxException e) {
            return new ArrayList<>();
        }
    }

    public static void saveTransaction(Context context, Transaction newTransaction) {
        String accountId = getAccountId(context);
        if (accountId != null) {
            ArrayList<Transaction> history = getHistory(context);
            if (history == null) history = new ArrayList<>();
            history.add(0, newTransaction);
            String json = new Gson().toJson(history);
            getPrefs(context).edit().putString(accountId + SUFFIX_TRANSACTION_HISTORY, json).apply();
        }
    }

    public static void logout(Context context) {
        getPrefs(context).edit().clear().apply();
    }

    // --- Data Classes ---
    public static class Account {
        private final String accountId;
        private final String privateKey;
        public final boolean isHardware;

        public Account(String accountId, String privateKey, boolean isHardware) {
            this.accountId = accountId;
            this.privateKey = privateKey;
            this.isHardware = isHardware;
        }

        public String getAccountId() { return accountId; }
        public String getPrivateKey() { return privateKey; }
    }

    public static class Contact implements Serializable {
        private final String name;
        private final String accountId;

        public Contact(String name, String accountId) {
            this.name = name;
            this.accountId = accountId;
        }

        public String getName() { return name; }
        public String getAccountId() { return accountId; }
    }
}
