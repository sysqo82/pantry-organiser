package com.pantry.organiser.ingestion.di;

import com.pantry.organiser.core.network.SyncService;
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
public final class IngestionModule_Companion_ProvideSyncServiceFactory implements Factory<SyncService> {
  private final Provider<HttpClient> clientProvider;

  public IngestionModule_Companion_ProvideSyncServiceFactory(Provider<HttpClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public SyncService get() {
    return provideSyncService(clientProvider.get());
  }

  public static IngestionModule_Companion_ProvideSyncServiceFactory create(
      Provider<HttpClient> clientProvider) {
    return new IngestionModule_Companion_ProvideSyncServiceFactory(clientProvider);
  }

  public static SyncService provideSyncService(HttpClient client) {
    return Preconditions.checkNotNullFromProvides(IngestionModule.Companion.provideSyncService(client));
  }
}
