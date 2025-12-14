package anwar.mlsa.hadera.aou;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import anwar.mlsa.hadera.aou.data.repository.TransactionRepositoryImpl;
import anwar.mlsa.hadera.aou.domain.repository.TransactionRepository;
import anwar.mlsa.hadera.aou.domain.use_case.SendTransactionUseCase;
import anwar.mlsa.hadera.aou.domain.use_case.VerifyAccountUseCase;

public class IdpayViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;

    public IdpayViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(IdpayViewModel.class)) {
            TransactionRepository repository = new TransactionRepositoryImpl(application);
            VerifyAccountUseCase verifyAccountUseCase = new VerifyAccountUseCase(repository);
            SendTransactionUseCase sendTransactionUseCase = new SendTransactionUseCase(repository);
            return (T) new IdpayViewModel(application, verifyAccountUseCase, sendTransactionUseCase);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
