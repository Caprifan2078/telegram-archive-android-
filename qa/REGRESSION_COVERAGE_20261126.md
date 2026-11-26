# Regression coverage ledger — 2026-11-26

Scope: current package org.openarchive.telegram.qa.integrated20261126; Android API 30 and 35; synthetic data only. Test class selection is not evidence of passing tests.

## Existing selections (46 classes)

- DrawerAesZipRegressionTest
- IndependentAesZipRegressionTest
- EncryptedTelegramImportRegressionTest
- ArchivePasswordOnDemandRegressionTest
- ArchivePasswordUiRegressionTest
- ArchiveReadOnlySafPipeRegressionTest
- SevenZTelegramImportRegressionTest
- RarCompatibilityRegressionTest
- ArchiveEndToEndRegressionTest
- UiMenuTourRegressionTest
- UiChromeOverlapRegressionTest
- ChronologyHubRegressionTest
- ChatVisualConsistencyRegressionTest
- MultiConversationHomeRegressionTest
- HistoryEventSearchRegressionTest
- QaVisualAuditScreenshotsTest
- MediaFastScrollRegressionTest
- MediaInfiniteGalleryRegressionTest
- FloatingRoundReattachRegressionTest
- FloatingRoundLifecycleRegressionTest
- RoundPlayerActivityTest
- MediaPlaybackTest
- MediaUpgradeRegressionTest
- Qa00MediaLifecycleTest
- VoiceSeekUiRegressionTest
- MusicRecoveryRegressionTest
- PlaybackLifecycleTest
- ProtectedArchiveSessionTest
- BackupDryRunRegressionTest
- BackupIntegrityAndroid15RegressionTest
- BackupSafReadbackRegressionTest
- FullBackupCustomAssetSafetyRegressionTest
- FullBackupSourceAccessRegressionTest
- PortableRestoreRegressionTest
- RestoreRecoveryTest
- ImportRollbackRegressionTest
- DuplicateTelegramIdSafetyTest
- SearchLifecycleRegressionTest
- PerformanceRegressionTest
- AttachmentPreviewScreenTest
- AnalyticsIntegrationTest
- AnalyticsSectionNavigationRegressionTest
- LightAnalyticsVisualTest
- TrendsIntegrationTest
- RelationshipAnalyticsExplanationUiTest
- RelationshipPaginationRegressionTest

## Added selections (43 classes)

### archive_deep (7)
- ArchiveIntegrationTest
- ArchivePreviewIntegrationTest
- LargeHtmlImportRegressionTest
- MultipleChatArchiveRegressionTest
- QaStandaloneHtmlImportRegressionTest
- RealTelegramExportStructureRegressionTest
- ZipFooterIntegrityRegressionTest

### media_export (9)
- ExportMediaFilenameRegressionTest
- MediaSaveIntegrationTest
- MediaAssetsRegressionTest
- MediaClassifierTest
- MediaGridResponsiveAndroidTest
- MediaTabsIsolationTest
- MissingMediaPlaybackRegressionTest
- OpusSeekDiagnosticTest
- TgsRenderingIntegrationTest

### chat_storage (6)
- CollectionsDatabaseTest
- CustomizationGroupTest
- MultiConversationRegistryRegressionTest
- QaTrashRestoreRegressionTest
- QuoteSavedStoreIntegrationTest
- TelegramReplyIntegrationTest

### migrations (4)
- ConversationPeriodsMigrationTest
- LocalProfileMigrationTest
- RelationshipsMigrationTest
- SchemaCollisionMigrationTest

### reading (7)
- PinnedFocusNavigationRegressionTest
- Qa01ChatListSizeTest
- Qa01ReadingPositionTest
- Qa01TwoChatsTest
- Qa02MissingAnchorTest
- Qa02ReadingPositionIsolationTest
- Qa02ReadingProgressIsolationTest

### ui_legacy (7)
- Qa00HelpLifecycleTest
- Qa00MediaFilterTest
- Qa00NavigationCoreTest
- Qa00RotationLifecycleTest
- QaUiChatNavigationRegressionTest
- ReadableUiTest
- UiSmokeTest

### history_export (3)
- ConversationPdfExporterTest
- GroupTimelineReadOnlyRegressionTest
- GroupTimelineScreenRegressionTest

## Not part of ordinary instrumentation

- ArchivePipeTestProvider: fixture provider, not a test class.
- SignedUpgradeSentinelTest: requires explicit seed/verify phases and two compatible signed APKs; ordinary invocation would skip. Verify separately before production upgrade claims.

89 of 89 ordinary instrumentation test classes are selected across the integrated, analytics and remaining-coverage workflows. Older UI tests may encode superseded UX; inspect each failure rather than skipping or blindly changing expectations. A QA APK is isolated and does not verify production data migration. Physical devices and visual inspection are separate from automated tests.
