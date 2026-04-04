package com.sudarshanchakra.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.sudarshanchakra.BuildConfig
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.api.AuthInterceptor
import com.sudarshanchakra.data.api.DynamicBaseUrlInterceptor
import com.sudarshanchakra.data.db.AlertDao
import com.sudarshanchakra.data.db.AppDatabase
import com.sudarshanchakra.data.db.MIGRATION_1_2
import com.sudarshanchakra.data.db.MIGRATION_2_3
import com.sudarshanchakra.data.db.MIGRATION_3_4
import com.sudarshanchakra.mdm.data.MdmAppUsageDao
import com.sudarshanchakra.mdm.data.MdmCallLogDao
import com.sudarshanchakra.mdm.data.MdmLocationDao
import com.sudarshanchakra.mdm.data.MdmScreenTimeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = com.sudarshanchakra.util.Constants.DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        // Fixed placeholder; [DynamicBaseUrlInterceptor] rewrites host to user-configured API.
        val base = DynamicBaseUrlInterceptor.PLACEHOLDER_RETROFIT_BASE.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sudarshanchakra_db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    @Singleton
    fun provideAlertDao(database: AppDatabase): AlertDao {
        return database.alertDao()
    }

    @Provides
    @Singleton
    fun provideMdmAppUsageDao(database: AppDatabase): MdmAppUsageDao = database.mdmAppUsageDao()

    @Provides
    @Singleton
    fun provideMdmCallLogDao(database: AppDatabase): MdmCallLogDao = database.mdmCallLogDao()

    @Provides
    @Singleton
    fun provideMdmScreenTimeDao(database: AppDatabase): MdmScreenTimeDao = database.mdmScreenTimeDao()

    @Provides
    @Singleton
    fun provideMdmLocationDao(database: AppDatabase): MdmLocationDao = database.mdmLocationDao()
}
