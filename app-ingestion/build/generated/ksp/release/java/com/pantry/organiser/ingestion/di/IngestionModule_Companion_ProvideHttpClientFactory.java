package com.pantry.organiser.ingestion.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
import javax.annotation.processing.Generated;

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
public final class IngestionModule_Companion_ProvideHttpClientFactory implements Factory<HttpClient> {
  @Override
  public HttpClient get() {
    return provideHttpClient();
  }

  public static IngestionModule_Companion_ProvideHttpClientFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static HttpClient provideHttpClient() {
    return Preconditions.checkNotNullFromProvides(IngestionModule.Companion.provideHttpClient());
  }

  private static final class InstanceHolder {
    private static final IngestionModule_Companion_ProvideHttpClientFactory INSTANCE = new IngestionModule_Companion_ProvideHttpClientFactory();
  }
}
