package nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAppRulesTest {
    private val global = NotificationSettings(
        privacy = MessagePrivacyMode.OFF,
        whenScreenOn = false,
        preferLongText = true,
        blockIfOutOfNotificationTimes = false,
        ignoreLowPriority = true,
    )

    private fun app(
        sendState: NotificationAppSendState = NotificationAppSendState.DEFAULT,
        pebbleState: NotificationAppSendState = NotificationAppSendState.DEFAULT,
        filter: NotificationAppFilter = NotificationAppFilter(),
        replacements: List<NotificationAppReplacement> = emptyList(),
        overrides: NotificationAppOverrides = NotificationAppOverrides(),
    ) = CompiledNotificationAppConfig(
        NotificationAppConfig(
            "com.example.app",
            sendState,
            pebbleState,
            filter,
            replacements,
            overrides,
        )
    )

    private fun allApps(
        filter: NotificationAppFilter = NotificationAppFilter(),
        replacements: List<NotificationAppReplacement> = emptyList(),
    ) = CompiledNotificationAppConfig(
        NotificationAppConfig(
            NotificationAppConfig.ALL_APPS,
            filter = filter,
            replacements = replacements
        )
    )

    private fun filter(
        mode: NotificationAppFilterMode,
        vararg entries: String,
        match: NotificationAppFilterMatch = NotificationAppFilterMatch.ANY,
        syntax: NotificationAppFilterSyntax = NotificationAppFilterSyntax.WORDS,
        ignoreCase: Boolean = false,
    ) = NotificationAppFilter(
        mode,
        match,
        syntax,
        ignoreCase,
        entries.toList(),
    )

    private fun passes(app: CompiledNotificationAppConfig, title: String?, body: String?) =
        NotificationAppRules.passesFilters(
            null,
            app,
            title,
            body,
        )

    @Test
    fun unconfiguredAppFollowsDefaultAction() {
        assertTrue(NotificationAppRules.shouldSend(null, NotificationDefaultAction.SEND))
        assertFalse(NotificationAppRules.shouldSend(null, NotificationDefaultAction.DO_NOT_SEND))
        assertTrue(NotificationAppRules.shouldSend(app(), NotificationDefaultAction.SEND))
        assertFalse(NotificationAppRules.shouldSend(app(), NotificationDefaultAction.DO_NOT_SEND))
    }

    @Test
    fun explicitSendStateOverridesDefaultAction() {
        assertTrue(
            NotificationAppRules.shouldSend(
                app(sendState = NotificationAppSendState.ALWAYS),
                NotificationDefaultAction.DO_NOT_SEND,
            )
        )
        assertFalse(
            NotificationAppRules.shouldSend(
                app(sendState = NotificationAppSendState.NEVER),
                NotificationDefaultAction.SEND,
            )
        )
    }

    @Test
    fun pebbleStateIsSeparate() {
        val config = app(sendState = NotificationAppSendState.NEVER, pebbleState = NotificationAppSendState.ALWAYS)
        assertFalse(NotificationAppRules.shouldSend(config, NotificationDefaultAction.SEND))
        assertTrue(NotificationAppRules.shouldSendPebble(config, NotificationDefaultAction.DO_NOT_SEND))
        assertTrue(
            NotificationAppRules.shouldSendPebble(
                app(sendState = NotificationAppSendState.NEVER),
                NotificationDefaultAction.SEND,
            )
        )
        assertFalse(
            NotificationAppRules.shouldSendPebble(
                null,
                NotificationDefaultAction.DO_NOT_SEND,
            )
        )
    }

    @Test
    fun filterOffAlwaysPasses() {
        val config = app(filter = filter(NotificationAppFilterMode.OFF, "Hello"))
        assertTrue(passes(config, "Hello", "world"))
    }

    @Test
    fun blockIfContainsAnyWord() {
        val config = app(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "spam", "ad"))
        assertFalse(passes(config, "Big ad", "text"))
        assertTrue(passes(config, "Hello", "world"))
    }

    @Test
    fun blockIfContainsAllWords() {
        val config = app(
            filter = filter(
                NotificationAppFilterMode.BLOCK_IF_CONTAINS,
                "spam",
                "ad",
                match = NotificationAppFilterMatch.ALL,
            )
        )
        assertTrue(passes(config, "Big ad", "text"))
        assertFalse(passes(config, "Big ad", "and spam"))
    }

    @Test
    fun onlySendIfContainsAnyWord() {
        val config = app(filter = filter(NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS, "urgent", "boss"))
        assertTrue(passes(config, "From boss", "hi"))
        assertFalse(passes(config, "From friend", "hi"))
    }

    @Test
    fun onlySendIfContainsAllWords() {
        val config = app(
            filter = filter(
                NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS,
                "urgent",
                "boss",
                match = NotificationAppFilterMatch.ALL,
            )
        )
        assertFalse(passes(config, "From boss", "hi"))
        assertTrue(passes(config, "From boss", "urgent"))
    }

    @Test
    fun filterMatchesTitleAndBodyJoinedBySpace() {
        val config = app(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "Hello world"))
        assertFalse(passes(config, "Hello", "world"))
        assertFalse(passes(config, null, "Hello world"))
    }

    @Test
    fun filterWithoutText() {
        val onlySend = app(filter = filter(NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS, "urgent"))
        assertFalse(passes(onlySend, null, null))
        assertFalse(passes(onlySend, "", ""))
        val block = app(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "urgent"))
        assertTrue(passes(block, null, null))
        assertTrue(passes(block, "", ""))
    }

    @Test
    fun wordsAreCaseSensitiveByDefault() {
        val config = app(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "Spam"))
        assertTrue(passes(config, "spam", ""))
        assertFalse(passes(config, "Spam", ""))
    }

    @Test
    fun wordsIgnoreCase() {
        val config = app(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "Spam", ignoreCase = true))
        assertFalse(passes(config, "SPAM", ""))
        assertFalse(passes(config, "spam", ""))
    }

    @Test
    fun wordsAreLiteral() {
        val config = app(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "a.c"))
        assertTrue(passes(config, "abc", ""))
        assertFalse(passes(config, "a.c", ""))
    }

    @Test
    fun regexIsFoundInText() {
        val config = app(
            filter = filter(
                NotificationAppFilterMode.BLOCK_IF_CONTAINS,
                "\\d{6}",
                syntax = NotificationAppFilterSyntax.REGEX,
            )
        )
        assertFalse(passes(config, "Your code", "is 123456"))
        assertTrue(passes(config, "Your code", "is 1234"))
    }

    @Test
    fun regexIgnoreCase() {
        val config = app(
            filter = filter(
                NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS,
                "^alert",
                syntax = NotificationAppFilterSyntax.REGEX,
                ignoreCase = true,
            )
        )
        assertTrue(passes(config, "ALERT now", ""))
        assertFalse(passes(config, "No alert", ""))
        assertTrue(passes(config, "", "ALERT now"))
    }

    @Test
    fun invalidRegexIsSkipped() {
        val config = app(
            filter = filter(
                NotificationAppFilterMode.BLOCK_IF_CONTAINS,
                "(",
                "spam",
                syntax = NotificationAppFilterSyntax.REGEX
            )
        )
        assertFalse(passes(config, "spam", ""))
        assertTrue(passes(config, "(", ""))
    }

    @Test
    fun filterWithOnlyInvalidEntriesPasses() {
        val config = app(
            filter = filter(
                NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS,
                "(",
                "",
                syntax = NotificationAppFilterSyntax.REGEX,
            )
        )
        assertTrue(passes(config, "anything", ""))
    }

    @Test
    fun allAppsAndAppFiltersMustBothPass() {
        val all = allApps(filter = filter(NotificationAppFilterMode.BLOCK_IF_CONTAINS, "spam"))
        val config = app(filter = filter(NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS, "boss"))
        assertTrue(NotificationAppRules.passesFilters(all, config, "boss", "hi"))
        assertFalse(NotificationAppRules.passesFilters(all, config, "boss", "spam"))
        assertFalse(NotificationAppRules.passesFilters(all, config, "friend", "hi"))
        assertFalse(NotificationAppRules.passesFilters(all, null, "spam", null))
        assertTrue(NotificationAppRules.passesFilters(null, null, "spam", null))
    }

    @Test
    fun replacementWithGroups() {
        val config = app(replacements = listOf(NotificationAppReplacement("(\\w+)@(\\w+)", "$2 at $1")))
        val text = NotificationAppRules.applyReplacements(null, config, "mail from bob@home", "bob@home wrote")
        assertEquals(NotificationText("mail from home at bob", "home at bob wrote"), text)
    }

    @Test
    fun replacementTargets() {
        val config = app(
            replacements = listOf(
                NotificationAppReplacement("a", "1", NotificationAppReplacementTarget.TITLE),
                NotificationAppReplacement("b", "2", NotificationAppReplacementTarget.BODY),
                NotificationAppReplacement("c", "3", NotificationAppReplacementTarget.BOTH),
            )
        )
        val text = NotificationAppRules.applyReplacements(null, config, "abc", "abc")
        assertEquals(NotificationText("1b3", "a23"), text)
    }

    @Test
    fun replacementsKeepNullText() {
        val config = app(replacements = listOf(NotificationAppReplacement("a", "b")))
        assertEquals(NotificationText(null, "b"), NotificationAppRules.applyReplacements(null, config, null, "a"))
    }

    @Test
    fun replacementsRunInOrderAllAppsFirst() {
        val all = allApps(replacements = listOf(NotificationAppReplacement("x", "y")))
        val config = app(
            replacements = listOf(
                NotificationAppReplacement("y", "z"),
                NotificationAppReplacement("z", "done"),
            )
        )
        assertEquals(
            NotificationText("done", null),
            NotificationAppRules.applyReplacements(all, config, "x", null)
        )
    }

    @Test
    fun replacementIgnoreCase() {
        val sensitive = app(replacements = listOf(NotificationAppReplacement("hello", "bye")))
        val insensitive = app(replacements = listOf(NotificationAppReplacement("hello", "bye", ignoreCase = true)))
        assertEquals("HELLO", NotificationAppRules.applyReplacements(null, sensitive, "HELLO", null).title)
        assertEquals("bye", NotificationAppRules.applyReplacements(null, insensitive, "HELLO", null).title)
    }

    @Test
    fun disabledReplacementIsSkipped() {
        val config = app(replacements = listOf(NotificationAppReplacement("a", "b", enabled = false)))
        assertEquals(
            NotificationText("a", "a"),
            NotificationAppRules.applyReplacements(null, config, "a", "a")
        )
    }

    @Test
    fun invalidReplacementsDoNotCrash() {
        val config = app(
            replacements = listOf(
                NotificationAppReplacement("(", "x"),
                NotificationAppReplacement("a", "$5"),
                NotificationAppReplacement("a", "\\"),
                NotificationAppReplacement("", "x"),
                NotificationAppReplacement("b", "c"),
            )
        )
        assertEquals(
            NotificationText("ac", "ac"),
            NotificationAppRules.applyReplacements(null, config, "ab", "ab")
        )
    }

    @Test
    fun bodyCanBecomeEmpty() {
        val config =
            app(replacements = listOf(NotificationAppReplacement(".*", "", NotificationAppReplacementTarget.BODY)))
        assertEquals(
            NotificationText("title", ""),
            NotificationAppRules.applyReplacements(null, config, "title", "some body")
        )
    }

    @Test
    fun replacementPreviewIgnoresEnabled() {
        val rule = CompiledNotificationAppReplacement(NotificationAppReplacement("a", "b", enabled = false))
        assertEquals("bbc", rule.apply("abc"))
    }

    @Test
    fun settingsWithoutAppConfigAreGlobal() {
        assertEquals(global, NotificationAppRules.resolveSettings(null, global))
        assertEquals(global, NotificationAppRules.resolveSettings(app(), global))
    }

    @Test
    fun overridesReplaceGlobalSettings() {
        val config = app(
            overrides = NotificationAppOverrides(
                privacy = NotificationAppPrivacyOverride.BODY_ONLY,
                whenScreenOn = NotificationAppToggleOverride.ON,
                preferLongText = NotificationAppToggleOverride.OFF,
                blockIfOutOfNotificationTimes = NotificationAppToggleOverride.ON,
                ignoreLowPriority = NotificationAppToggleOverride.OFF,
            )
        )
        val expected = NotificationSettings(
            privacy = MessagePrivacyMode.BODY_ONLY,
            whenScreenOn = true,
            preferLongText = false,
            blockIfOutOfNotificationTimes = true,
            ignoreLowPriority = false,
        )
        assertEquals(expected, NotificationAppRules.resolveSettings(config, global))
    }

    @Test
    fun partialOverrides() {
        val config = app(
            overrides = NotificationAppOverrides(
                privacy = NotificationAppPrivacyOverride.OFF,
                blockIfOutOfNotificationTimes = NotificationAppToggleOverride.OFF
            )
        )
        val globalHidden = global.copy(privacy = MessagePrivacyMode.COMPLETE, blockIfOutOfNotificationTimes = true)
        assertEquals(global, NotificationAppRules.resolveSettings(config, globalHidden))
    }

    @Test
    fun regexError() {
        assertNull(NotificationAppRules.regexError("\\d+"))
        assertNotNull(NotificationAppRules.regexError("("))
    }

    @Test
    fun replacementError() {
        assertNull(NotificationAppRules.replacementError("(\\d+)-(?<b>\\d+)", "$2 \${b} $1 \\$"))
        assertNull(NotificationAppRules.replacementError("\\d+", "***"))
        assertEquals(
            "Illegal group reference: group index is missing",
            NotificationAppRules.replacementError("\\d+", "card: $")
        )
        assertEquals(
            "No group 2",
            NotificationAppRules.replacementError("(\\d+)", "$2")
        )
        assertEquals(
            "No group with name {missing}",
            NotificationAppRules.replacementError("\\d+", "\${missing}")
        )
        assertEquals(
            "character to be escaped is missing",
            NotificationAppRules.replacementError("\\d+", "a\\")
        )
    }

    @Test
    fun isConfigured() {
        assertFalse(NotificationAppConfig("com.example.app").isConfigured)
        assertTrue(NotificationAppConfig("com.example.app", sendState = NotificationAppSendState.NEVER).isConfigured)
        assertTrue(
            NotificationAppConfig(
                "com.example.app",
                replacements = listOf(NotificationAppReplacement("a"))
            ).isConfigured
        )
        assertTrue(
            NotificationAppConfig(
                "com.example.app",
                overrides = NotificationAppOverrides(ignoreLowPriority = NotificationAppToggleOverride.OFF)
            ).isConfigured
        )
    }

    @Test
    fun privacyModeFromPrefValue() {
        assertEquals(MessagePrivacyMode.OFF, MessagePrivacyMode.fromPrefValue("off"))
        assertEquals(MessagePrivacyMode.COMPLETE, MessagePrivacyMode.fromPrefValue("complete"))
        assertEquals(MessagePrivacyMode.BODY_ONLY, MessagePrivacyMode.fromPrefValue("bodyonly"))
        assertEquals(MessagePrivacyMode.OFF, MessagePrivacyMode.fromPrefValue(null))
    }
}
