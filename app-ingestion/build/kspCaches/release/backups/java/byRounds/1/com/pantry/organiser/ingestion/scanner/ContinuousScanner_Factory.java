package com.pantry.organiser.ingestion.scanner;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ContinuousScanner_Factory implements Factory<ContinuousScanner> {
  private final Provider<Context> contextProvider;

  public ContinuousScanner_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ContinuousScanner get() {
    return newInstance(contextProvider.get());
  }

  public static ContinuousScanner_Factory create(Provider<Context> contextProvider) {
    return new ContinuousScanner_Factory(contextProvider);
  }

  public static ContinuousScanner newInstance(Context context) {
    return new ContinuousScanner(context);
  }
}
