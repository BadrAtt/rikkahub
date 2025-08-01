package me.rerere.rikkahub.di

import androidx.room.Room
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.ai.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.TemplateTransformer
import me.rerere.rikkahub.data.api.RikkaHubAPI
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import me.rerere.rikkahub.data.mcp.McpManager
import me.rerere.rikkahub.data.sync.DataSync
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

val dataSourceModule = module {
    single {
        SettingsStore(context = get(), scope = get())
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "rikka_hub")
            .addMigrations(Migration_6_7)
            .build()
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single { McpManager(settingsStore = get(), appScope = get()) }

    single {
        GenerationHandler(
            context = get(),
            json = get(),
            memoryRepo = get(),
            conversationRepo = get()
        )
    }

    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    single {
        DataSync(settingsStore = get(), json = get(), context = get())
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .addConverterFactory(get<Json>().asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    single<RikkaHubAPI> {
        get<Retrofit>().create(RikkaHubAPI::class.java)
    }
}
