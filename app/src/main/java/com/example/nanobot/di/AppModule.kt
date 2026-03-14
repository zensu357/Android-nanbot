package com.example.nanobot.di

import android.content.Context
import androidx.room.Room
import com.example.nanobot.core.database.NanobotDatabase
import com.example.nanobot.core.database.dao.CustomSkillDao
import com.example.nanobot.core.ai.AgentOrchestrator
import com.example.nanobot.core.ai.AgentTurnRunner
import com.example.nanobot.core.ai.HeartbeatDecisionEngine
import com.example.nanobot.core.ai.MemoryRefreshScheduler
import com.example.nanobot.core.ai.RealtimeMemoryRefreshScheduler
import com.example.nanobot.core.mcp.HttpMcpClient
import com.example.nanobot.core.mcp.McpClient
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpRegistryImpl
import com.example.nanobot.core.notifications.HeartbeatNotificationSink
import com.example.nanobot.core.notifications.HeartbeatNotifier
import com.example.nanobot.core.notifications.ReminderNotificationSink
import com.example.nanobot.core.notifications.ReminderNotifier
import com.example.nanobot.core.preferences.McpServerConfigStore
import com.example.nanobot.core.preferences.McpServerStore
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.skills.SkillDirectoryScanner
import com.example.nanobot.core.skills.SkillImportScanner
import com.example.nanobot.core.database.dao.MessageDao
import com.example.nanobot.core.database.dao.MemoryFactDao
import com.example.nanobot.core.database.dao.MemorySummaryDao
import com.example.nanobot.core.database.dao.ReminderDao
import com.example.nanobot.core.database.dao.SessionDao
import com.example.nanobot.core.web.DefaultWebSearchEndpointProvider
import com.example.nanobot.core.web.WebRequestGuard
import com.example.nanobot.core.web.SafeDns
import com.example.nanobot.core.web.WebAccessConfigProvider
import com.example.nanobot.core.web.WebAccessConfigProviderImpl
import com.example.nanobot.core.web.WebSearchEndpointProvider
import com.example.nanobot.core.worker.WorkerSchedulingController
import com.example.nanobot.core.worker.ReminderWorkScheduler
import com.example.nanobot.domain.repository.SkillRepository
import com.example.nanobot.domain.repository.SessionRepository
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.domain.repository.HeartbeatRepository
import com.example.nanobot.domain.repository.MemoryRepository
import com.example.nanobot.domain.repository.ReminderRepository
import com.example.nanobot.domain.repository.WebAccessRepository
import com.example.nanobot.domain.repository.WorkspaceRepository
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.tools.ToolAccessPolicy
import com.example.nanobot.core.tools.ToolValidator
import com.example.nanobot.core.tools.impl.DeviceTimeTool
import com.example.nanobot.core.tools.impl.DelegateTaskTool
import com.example.nanobot.core.tools.impl.ActivateSkillTool
import com.example.nanobot.core.tools.impl.ListWorkspaceTool
import com.example.nanobot.core.tools.impl.MemoryLookupTool
import com.example.nanobot.core.tools.impl.NotifyUserTool
import com.example.nanobot.core.tools.impl.InputTextTool
import com.example.nanobot.core.tools.impl.LaunchAppTool
import com.example.nanobot.core.tools.impl.PressGlobalActionTool
import com.example.nanobot.core.tools.impl.ReadFileTool
import com.example.nanobot.core.tools.impl.ReadCurrentUiTool
import com.example.nanobot.core.tools.impl.ReadSkillResourceTool
import com.example.nanobot.core.tools.impl.ReplaceInFileTool
import com.example.nanobot.core.tools.impl.ScheduleReminderTool
import com.example.nanobot.core.tools.impl.ScrollUiTool
import com.example.nanobot.core.tools.impl.SearchWorkspaceTool
import com.example.nanobot.core.tools.impl.SessionSnapshotTool
import com.example.nanobot.core.tools.impl.TapUiNodeTool
import com.example.nanobot.core.tools.impl.WaitForUiTool
import com.example.nanobot.core.tools.impl.WebFetchTool
import com.example.nanobot.core.tools.impl.WebSearchTool
import com.example.nanobot.core.tools.impl.WriteFileTool
import com.example.nanobot.data.repository.ChatRepositoryImpl
import com.example.nanobot.data.repository.HeartbeatRepositoryImpl
import com.example.nanobot.data.repository.MemoryRepositoryImpl
import com.example.nanobot.data.repository.ReminderRepositoryImpl
import com.example.nanobot.data.repository.SessionRepositoryImpl
import com.example.nanobot.data.repository.SkillRepositoryImpl
import com.example.nanobot.data.repository.WebAccessRepositoryImpl
import com.example.nanobot.data.repository.WorkspaceRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NanobotDatabase =
        Room.databaseBuilder(
            context,
            NanobotDatabase::class.java,
            "nanobot.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    @Singleton
    fun provideAgentTurnRunner(orchestrator: AgentOrchestrator): AgentTurnRunner = orchestrator

    @Provides
    @Singleton
    fun provideMemoryRefreshScheduler(impl: RealtimeMemoryRefreshScheduler): MemoryRefreshScheduler = impl

    @Provides
    @Singleton
    fun provideHeartbeatDecisionEngine(decider: com.example.nanobot.core.ai.HeartbeatDecider): HeartbeatDecisionEngine = decider

    @Provides
    @Singleton
    fun provideHeartbeatNotificationSink(notifier: HeartbeatNotifier): HeartbeatNotificationSink = notifier

    @Provides
    @Singleton
    fun provideReminderNotificationSink(notifier: ReminderNotifier): ReminderNotificationSink = notifier

    @Provides
    @Singleton
    fun provideMcpClient(client: HttpMcpClient): McpClient = client

    @Provides
    @Singleton
    fun provideMcpServerStore(store: McpServerStore): McpServerConfigStore = store

    @Provides
    @Singleton
    fun provideMcpRegistry(impl: McpRegistryImpl): McpRegistry = impl

    @Provides
    @Singleton
    fun provideSettingsConfigStore(store: com.example.nanobot.core.preferences.SettingsDataStore): SettingsConfigStore = store

    @Provides
    @Singleton
    fun provideWorkerSchedulingController(scheduler: com.example.nanobot.core.worker.NanobotWorkerScheduler): WorkerSchedulingController = scheduler

    @Provides
    @Singleton
    fun provideReminderWorkScheduler(scheduler: com.example.nanobot.core.worker.NanobotWorkerScheduler): ReminderWorkScheduler = scheduler

    @Provides
    fun provideSessionDao(database: NanobotDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideMessageDao(database: NanobotDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideMemoryFactDao(database: NanobotDatabase): MemoryFactDao = database.memoryFactDao()

    @Provides
    fun provideMemorySummaryDao(database: NanobotDatabase): MemorySummaryDao = database.memorySummaryDao()

    @Provides
    fun provideReminderDao(database: NanobotDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideCustomSkillDao(database: NanobotDatabase): CustomSkillDao = database.customSkillDao()

    @Provides
    @Singleton
    fun provideSessionRepository(impl: SessionRepositoryImpl): SessionRepository = impl

    @Provides
    @Singleton
    fun provideChatRepository(impl: ChatRepositoryImpl): ChatRepository = impl

    @Provides
    @Singleton
    fun provideHeartbeatRepository(impl: HeartbeatRepositoryImpl): HeartbeatRepository = impl

    @Provides
    @Singleton
    fun provideMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository = impl

    @Provides
    @Singleton
    fun provideReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository = impl

    @Provides
    @Singleton
    fun provideWorkspaceRepository(impl: WorkspaceRepositoryImpl): WorkspaceRepository = impl

    @Provides
    @Singleton
    fun provideWebAccessRepository(impl: WebAccessRepositoryImpl): WebAccessRepository = impl

    @Provides
    @Singleton
    fun provideSkillRepository(impl: SkillRepositoryImpl): SkillRepository = impl

    @Provides
    @Singleton
    fun provideSkillImportScanner(scanner: SkillDirectoryScanner): SkillImportScanner = scanner

    @Provides
    @Singleton
    fun provideWebAccessConfigProvider(impl: WebAccessConfigProviderImpl): WebAccessConfigProvider = impl

    @Provides
    @Singleton
    fun provideWebSearchEndpointProvider(impl: DefaultWebSearchEndpointProvider): WebSearchEndpointProvider = impl

    @Provides
    @Singleton
    fun provideWebRequestGuard(): WebRequestGuard = WebRequestGuard()

    @Provides
    @Singleton
    fun provideSafeDns(webRequestGuard: WebRequestGuard): SafeDns = SafeDns(webRequestGuard)

    @Provides
    @Singleton
    fun provideToolRegistry(
        accessPolicy: ToolAccessPolicy,
        validator: ToolValidator,
        mcpRegistry: McpRegistry,
        notifyUserTool: NotifyUserTool,
        delegateTaskTool: DelegateTaskTool,
        activateSkillTool: ActivateSkillTool,
        deviceTimeTool: DeviceTimeTool,
        listWorkspaceTool: ListWorkspaceTool,
        readFileTool: ReadFileTool,
        readSkillResourceTool: ReadSkillResourceTool,
        writeFileTool: WriteFileTool,
        replaceInFileTool: ReplaceInFileTool,
        searchWorkspaceTool: SearchWorkspaceTool,
        webFetchTool: WebFetchTool,
        webSearchTool: WebSearchTool,
        sessionSnapshotTool: SessionSnapshotTool,
        memoryLookupTool: MemoryLookupTool,
        scheduleReminderTool: ScheduleReminderTool,
        readCurrentUiTool: ReadCurrentUiTool,
        tapUiNodeTool: TapUiNodeTool,
        inputTextTool: InputTextTool,
        scrollUiTool: ScrollUiTool,
        pressGlobalActionTool: PressGlobalActionTool,
        launchAppTool: LaunchAppTool,
        waitForUiTool: WaitForUiTool
    ): ToolRegistry {
        return ToolRegistry(validator, accessPolicy, mcpRegistry).apply {
            register(notifyUserTool)
            register(delegateTaskTool)
            register(activateSkillTool)
            register(deviceTimeTool)
            register(listWorkspaceTool)
            register(readFileTool)
            register(readSkillResourceTool)
            register(writeFileTool)
            register(replaceInFileTool)
            register(searchWorkspaceTool)
            register(webFetchTool)
            register(webSearchTool)
            register(sessionSnapshotTool)
            register(memoryLookupTool)
            register(scheduleReminderTool)
            register(readCurrentUiTool)
            register(tapUiNodeTool)
            register(inputTextTool)
            register(scrollUiTool)
            register(pressGlobalActionTool)
            register(launchAppTool)
            register(waitForUiTool)
        }
    }
}
