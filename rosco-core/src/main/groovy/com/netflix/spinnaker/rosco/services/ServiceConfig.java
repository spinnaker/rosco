/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
public class ServiceConfig {
  @Value("${services.clouddriver.base-url:http://localhost:7002}")
  String clouddriverBaseUrl;

  @Value("${retrofit.log-level:BASIC}")
  String retrofitLogLevel;

  @Autowired DefaultOkHttpClientBuilderProvider clientBuilderProvider;

  @Bean
  Ok3Client okClient(OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Ok3Client(okHttpClientConfig.create().build());
  }

  @Bean
  RetrySupport retrySupport() {
    return new RetrySupport();
  }

  // This should be service-agnostic if more integrations than clouddriver are used
  @Bean
  ClouddriverService clouddriverService(
      OkHttp3MetricsInterceptor okHttp3MetricsInterceptor,
      SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor) {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    // using DefaultOkHttpClientBuilderProvider instead of OkHttp3ClientConfiguration.create(), as
    // okHttpClient
    // returning from OkHttp3ClientConfiguration.create() already included
    // okHttp3MetricsInterceptor,
    // but in retrofit2 RequestInterceptor is removed so we have to use okhttp3.Interceptor to add
    // spinnaker headers.
    // Interceptor immutable list and sequential so warn logs will be printed due to header not
    // visible in okHttp3MetricsInterceptor.s
    OkHttpClient okHttpClient =
        clientBuilderProvider
            .get(new DefaultServiceEndpoint("clouddriver", clouddriverBaseUrl))
            .addInterceptor(spinnakerRequestHeaderInterceptor)
            .addInterceptor(okHttp3MetricsInterceptor)
            .addInterceptor(
                new HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.valueOf(retrofitLogLevel)))
            .build();

    return new Retrofit.Builder()
        .baseUrl(clouddriverBaseUrl)
        .client(okHttpClient)
        .addCallAdapterFactory(
            ErrorHandlingExecutorCallAdapterFactory.getInstance(
                new ErrorHandlingExecutorCallAdapterFactory.MainThreadExecutor()))
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build()
        .create(ClouddriverService.class);
  }
}
