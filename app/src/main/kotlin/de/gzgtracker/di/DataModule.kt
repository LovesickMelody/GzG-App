package de.gzgtracker.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.gzgtracker.BuildConfig
import de.gzgtracker.data.local.AccountDao
import de.gzgtracker.data.local.GzgDatabase
import de.gzgtracker.data.local.PromoActionDao
import de.gzgtracker.data.local.SubmissionDao
import de.gzgtracker.data.remote.ActionsApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GzgDatabase =
        Room.databaseBuilder(context, GzgDatabase::class.java, GzgDatabase.NAME).build()

    @Provides
    fun accountDao(database: GzgDatabase): AccountDao = database.accountDao()

    @Provides
    fun promoActionDao(database: GzgDatabase): PromoActionDao = database.promoActionDao()

    @Provides
    fun submissionDao(database: GzgDatabase): SubmissionDao = database.submissionDao()

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Die eigentliche Adresse kommt pro Aufruf ueber @Url aus den Einstellungen.
        // Diese Basis wird nie benutzt, Retrofit verlangt sie aber.
        .baseUrl("https://raw.githubusercontent.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun actionsApi(retrofit: Retrofit): ActionsApi = retrofit.create(ActionsApi::class.java)
}
