package com.pantry.organiser.ingestion;

import com.pantry.organiser.core.network.OpenFoodFactsRepository;
import com.pantry.organiser.core.network.SyncService;
import com.pantry.organiser.ingestion.scanner.ContinuousScanner;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class IngestionViewModel_Factory implements Factory<IngestionViewModel> {
  private final Provider<ContinuousScanner> scannerProvider;

  private final Provider<FeedbackController> feedbackControllerProvider;

  private final Provider<SyncService> syncServiceProvider;

  private final Provider<OpenFoodFactsRepository> offRepositoryProvider;

  public IngestionViewModel_Factory(Provider<ContinuousScanner> scannerProvider,
      Provider<FeedbackController> feedbackControllerProvider,
      Provider<SyncService> syncServiceProvider,
      Provider<OpenFoodFactsRepository> offRepositoryProvider) {
    this.scannerProvider = scannerProvider;
    this.feedbackControllerProvider = feedbackControllerProvider;
    this.syncServiceProvider = syncServiceProvider;
    this.offRepositoryProvider = offRepositoryProvider;
  }

  @Override
  public IngestionViewModel get() {
    return newInstance(scannerProvider.get(), feedbackControllerProvider.get(), syncServiceProvider.get(), offRepositoryProvider.get());
  }

  public static IngestionViewModel_Factory create(Provider<ContinuousScanner> scannerProvider,
      Provider<FeedbackController> feedbackControllerProvider,
      Provider<SyncService> syncServiceProvider,
      Provider<OpenFoodFactsRepository> offRepositoryProvider) {
    return new IngestionViewModel_Factory(scannerProvider, feedbackControllerProvider, syncServiceProvider, offRepositoryProvider);
  }

  public static IngestionViewModel newInstance(ContinuousScanner scanner,
      FeedbackController feedbackController, SyncService syncService,
      OpenFoodFactsRepository offRepository) {
    return new IngestionViewModel(scanner, feedbackController, syncService, offRepository);
  }
}
