package com.pantry.organiser.ingestion.di;

import com.pantry.organiser.core.network.OpenFoodFactsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class IngestionModule_Companion_ProvideOpenFoodFactsRepositoryFactory implements Factory<OpenFoodFactsRepository> {
  private final Provider<HttpClient> clientProvider;

  public IngestionModule_Companion_ProvideOpenFoodFactsRepositoryFactory(
      Provider<HttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public OpenFoodFactsRepository get() {
    return provideOpenFoodFactsRepository(clientProvider.get());
  }

  public static IngestionModule_Companion_ProvideOpenFoodFactsRepositoryFactory create(
      Provider<HttpClient> clientProvider) {
    return new IngestionModule_Companion_ProvideOpenFoodFactsRepositoryFactory(clientProvider);
  }

  public static OpenFoodFactsRepository provideOpenFoodFactsRepository(HttpClient client) {
    return Preconditions.checkNotNullFromProvides(IngestionModule.Companion.provideOpenFoodFactsRepository(client));
  }
}
